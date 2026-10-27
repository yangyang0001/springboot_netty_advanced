package deepblue.inaction_02_springboot.handler.inbound;

import com.alibaba.fastjson.JSON;
import deepblue.inaction_02_springboot.model.Message;
import deepblue.inaction_02_springboot.model.MessageEnum;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.timeout.IdleState;
import io.netty.handler.timeout.IdleStateEvent;
import lombok.extern.slf4j.Slf4j;

/**
 * 客户端 Handler（MineNettyClient 使用，不是 Spring Bean）：
 * 打印收到的消息、写空闲时发心跳、断线时回调重连（被踢下线时不重连）。
 * 每个连接 new 一个实例，所以 kicked 状态只属于当前连接。
 */
@Slf4j
public class ClientHandler extends SimpleChannelInboundHandler<Message> {

    private final String userId;
    private final Runnable onDisconnected;
    private boolean kicked;

    public ClientHandler(String userId, Runnable onDisconnected) {
        this.userId = userId;
        this.onDisconnected = onDisconnected;
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        log.info("ClientHandler channelActive invoke, {} {} connected: {} -> {}", channelId(ctx), userId,
                ctx.channel().localAddress(), ctx.channel().remoteAddress());
        super.channelActive(ctx);
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, Message msg) {
        if (msg.getType() == MessageEnum.KICKED) {
            kicked = true;
        }
        if (msg.getType() != null && msg.getType().isHeartbeat()) {
            log.debug("ClientHandler channelRead0 invoke, {} << {}", channelId(ctx), JSON.toJSONString(msg));
        } else {
            log.info("ClientHandler channelRead0 invoke, {} << {}", channelId(ctx), JSON.toJSONString(msg));
        }
    }

    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
        if (evt instanceof IdleStateEvent e && e.state() == IdleState.WRITER_IDLE) {
            // ⑥ 写空闲：一段时间没发数据，主动发心跳，防止被服务端的读空闲踢掉。
            log.debug("ClientHandler userEventTriggered invoke, {} writer idle, send PING", channelId(ctx));
            ctx.writeAndFlush(new Message(MessageEnum.PING, userId, null, null));
        } else {
            super.userEventTriggered(ctx, evt);
        }
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) {
        if (kicked) {
            // ⑥ 被踢下线是服务端的主动决定，重连只会把对方再踢掉，所以不重连。
            log.warn("ClientHandler channelInactive invoke, {} kicked offline, will not reconnect (type quit to exit)", channelId(ctx));
            return;
        }
        log.info("ClientHandler channelInactive invoke, {} disconnected", channelId(ctx));
        onDisconnected.run();
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        log.error("ClientHandler exceptionCaught invoke, {} channel error", channelId(ctx), cause);
        ctx.close();
    }

    private static String channelId(ChannelHandlerContext ctx) {
        return ctx.channel().id().asShortText();
    }
}
