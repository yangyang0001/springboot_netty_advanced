package deepblue.inaction_02_springboot.session;

import deepblue.inaction_02_springboot.model.Message;
import deepblue.inaction_02_springboot.model.MessageType;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFutureListener;
import io.netty.util.AttributeKey;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * userId ⇄ Channel 的映射，是 Spring 世界（Controller / Service）和 Netty 世界（Channel）之间的桥。
 * 任意线程都可以调用 channel.writeAndFlush，Netty 会把它转成任务交给该 Channel 的 EventLoop 执行。
 */
@Slf4j
@Component
public class ChannelSessionManager {

    public static final AttributeKey<String> USER_ID = AttributeKey.valueOf("userId");

    private final Map<String, Channel> channels = new ConcurrentHashMap<>();

    public void bind(String userId, Channel channel) {
        channel.attr(USER_ID).set(userId);
        Channel old = channels.put(userId, channel);
        if (old != null && old != channel) {
            // 同一个 userId 重复登录：先通知旧连接被踢下线，写出完成后再关闭，避免客户端重连后互相踢。
            old.attr(USER_ID).set(null);
            old.writeAndFlush(Message.of(MessageType.KICKED, userId + " logged in elsewhere"))
                    .addListener(ChannelFutureListener.CLOSE);
        }
    }

    public void unbind(Channel channel) {
        String userId = channel.attr(USER_ID).get();
        if (userId != null) {
            // 只移除自己，避免把重复登录后的新连接误删。
            channels.remove(userId, channel);
        }
    }

    public Set<String> onlineUsers() {
        return Set.copyOf(channels.keySet());
    }

    /** 推送给指定用户，返回是否已提交写出（用户离线或写缓冲区已满时返回 false）。 */
    public boolean send(String userId, Message message) {
        Channel channel = userId == null ? null : channels.get(userId);
        if (channel == null || !channel.isActive()) {
            return false;
        }
        // ⑦ 写缓冲区超过高水位，说明对端消费慢，直接拒绝，避免 OOM。
        if (!channel.isWritable()) {
            log.warn("channel not writable, drop message to {}", userId);
            return false;
        }
        // ⑤ 写出是异步的，失败时关闭连接。
        channel.writeAndFlush(message).addListener(ChannelFutureListener.CLOSE_ON_FAILURE);
        return true;
    }

    public int broadcast(Message message) {
        int count = 0;
        for (String userId : channels.keySet()) {
            if (send(userId, message)) {
                count++;
            }
        }
        return count;
    }
}
