package deepblue.inaction_03_app.handler.inbound;

import com.alibaba.fastjson.JSON;
import deepblue.inaction_03_app.client.ClientListener;
import deepblue.inaction_03_app.model.AppMessage;
import deepblue.inaction_03_app.model.AppMessageType;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.timeout.IdleState;
import io.netty.handler.timeout.IdleStateEvent;
import lombok.extern.slf4j.Slf4j;

/**
 * App 客户端 Handler：逻辑和 inaction_02 的 ClientHandler 相同，区别是使用 AppMessage，并把事件交给 ClientListener，由界面层展示。
 * 每个连接 new 一个实例，所以 kicked 状态只属于当前连接。
 */
@Slf4j
public class AppClientHandler extends SimpleChannelInboundHandler<AppMessage> {

    private final String userId;
    private final ClientListener listener;
    private final Runnable onDisconnected;
    private boolean kicked;

    public AppClientHandler(String userId, ClientListener listener, Runnable onDisconnected) {
        this.userId = userId;
        this.listener = listener;
        this.onDisconnected = onDisconnected;
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        log.info("AppClientHandler channelActive invoke, userId={}, {} connected: {} -> {}", userId, channelId(ctx),
                ctx.channel().localAddress(), ctx.channel().remoteAddress());
        super.channelActive(ctx);
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, AppMessage msg) {
        if (msg.getType() == AppMessageType.KICKED) {
            kicked = true;
        }
        if (msg.getType() != null && msg.getType().isHeartbeat()) {
            log.debug("AppClientHandler channelRead0 invoke, userId={}, {} << {}", userId, channelId(ctx), JSON.toJSONString(msg));
        } else {
            log.info("AppClientHandler channelRead0 invoke, userId={}, {} << {}", userId, channelId(ctx), JSON.toJSONString(msg));
        }
        // ① 这里是 EventLoop 线程，界面层收到回调后要自己切回界面线程。
        listener.onMessage(msg);
    }

    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
        if (evt instanceof IdleStateEvent e && e.state() == IdleState.WRITER_IDLE) {
            // ⑥ 写空闲：一段时间没发数据，主动发心跳，防止被服务端的读空闲踢掉。
            log.debug("AppClientHandler userEventTriggered invoke, userId={}, {} writer idle, send PING", userId, channelId(ctx));
            ctx.writeAndFlush(new AppMessage(AppMessageType.PING, userId, null, null));
        } else {
            super.userEventTriggered(ctx, evt);
        }
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) {
        if (kicked) {
            // ⑥ 被踢下线是服务端的主动决定，重连只会把对方再踢掉，所以不重连。
            log.warn("AppClientHandler channelInactive invoke, userId={}, {} kicked offline, will not reconnect", userId, channelId(ctx));
            listener.onKicked();
            return;
        }
        log.info("AppClientHandler channelInactive invoke, userId={}, {} disconnected", userId, channelId(ctx));
        listener.onDisconnected();
        onDisconnected.run();
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        log.error("AppClientHandler exceptionCaught invoke, userId={}, {} channel error", userId, channelId(ctx), cause);
        ctx.close();
    }

    private static String channelId(ChannelHandlerContext ctx) {
        return ctx.channel().id().asShortText();
    }
}
