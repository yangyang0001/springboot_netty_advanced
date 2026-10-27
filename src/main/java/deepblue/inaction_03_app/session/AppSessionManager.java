package deepblue.inaction_03_app.session;

import deepblue.inaction_03_app.model.AppMessage;
import deepblue.inaction_03_app.model.AppMessageType;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.util.AttributeKey;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * App 服务端的会话表：userId ⇄ Channel。
 * 和 inaction_02 的 ChannelSessionManager 相比，多了上线 / 下线广播：
 * - 新用户上线（bind 时没有旧连接）→ 给其他人广播 USER_ONLINE；
 * - 用户下线（unbind 真正移除了自己）→ 给其他人广播 USER_OFFLINE；
 * - 重复登录踢人时，这个 userId 一直在线，所以不广播。
 */
@Slf4j
@Component
public class AppSessionManager {

    public static final AttributeKey<String> USER_ID = AttributeKey.valueOf("appUserId");

    private final Map<String, Channel> channels = new ConcurrentHashMap<>();

    /** 日志用：取 Channel 上绑定的 userId，未登录时返回 "-"。 */
    public static String userId(Channel channel) {
        String userId = channel.attr(USER_ID).get();
        return userId == null ? "-" : userId;
    }

    public void bind(String userId, Channel channel) {
        channel.attr(USER_ID).set(userId);
        Channel old = channels.put(userId, channel);
        log.info("AppSessionManager bind invoke, userId={}, {} bind online={}", userId, channel.id().asShortText(), channels.size());
        if (old == null) {
            broadcastExcept(userId, AppMessage.of(AppMessageType.USER_ONLINE, userId));
            return;
        }
        if (old != channel) {
            // 同一个 userId 重复登录：先通知旧连接被踢下线，写出完成后再关闭；清空旧连接的 userId，它断开时 unbind 不会误删新连接。
            log.info("AppSessionManager bind invoke, userId={}, {} kick old channel, new channel={}",
                    userId, old.id().asShortText(), channel.id().asShortText());
            old.attr(USER_ID).set(null);
            old.writeAndFlush(AppMessage.of(AppMessageType.KICKED, userId + " logged in elsewhere"))
                    .addListener((ChannelFuture f) -> {
                        log.info("AppSessionManager bind invoke, userId={}, {} kicked notice written={}, close", userId, old.id().asShortText(), f.isSuccess());
                        f.channel().close();
                    });
        }
    }

    public void unbind(Channel channel) {
        String userId = channel.attr(USER_ID).get();
        if (userId == null) {
            return;
        }
        // 只移除自己，避免把重复登录后的新连接误删。
        boolean removed = channels.remove(userId, channel);
        log.info("AppSessionManager unbind invoke, userId={}, {} unbind removed={} online={}",
                userId, channel.id().asShortText(), removed, channels.size());
        if (removed) {
            broadcastExcept(userId, AppMessage.of(AppMessageType.USER_OFFLINE, userId));
        }
    }

    /** 当前在线用户（不含 userId 自己），按名字排序。 */
    public List<String> onlineUsersExcept(String userId) {
        return channels.keySet().stream().filter(u -> !u.equals(userId)).sorted().toList();
    }

    /** 推送给指定用户，返回是否已提交写出（用户离线或写缓冲区已满时返回 false）。 */
    public boolean send(String userId, AppMessage message) {
        Channel channel = userId == null ? null : channels.get(userId);
        if (channel == null || !channel.isActive()) {
            log.info("AppSessionManager send invoke, userId={}, skipped: offline, msg={}", userId, message);
            return false;
        }
        String channelId = channel.id().asShortText();
        // ⑦ 写缓冲区超过高水位，说明对端消费慢，直接拒绝，避免 OOM。
        if (!channel.isWritable()) {
            log.warn("AppSessionManager send invoke, userId={}, {} channel not writable, drop message: {}", userId, channelId, message);
            return false;
        }
        log.info("AppSessionManager send invoke, userId={}, {} send: {}", userId, channelId, message);
        // ⑤ 写出是异步的，失败时记录原因并关闭连接。
        channel.writeAndFlush(message).addListener((ChannelFuture f) -> {
            if (!f.isSuccess()) {
                log.warn("AppSessionManager send invoke, userId={}, {} send failed, close: {}", userId, channelId, message, f.cause());
                f.channel().close();
            }
        });
        return true;
    }

    /** 广播给除 exceptUserId 以外的所有在线用户。 */
    public void broadcastExcept(String exceptUserId, AppMessage message) {
        int count = 0;
        for (String userId : channels.keySet()) {
            if (!userId.equals(exceptUserId) && send(userId, message)) {
                count++;
            }
        }
        log.info("AppSessionManager broadcastExcept invoke, userId={}, broadcast {} sent={}", exceptUserId, message.getType(), count);
    }
}
