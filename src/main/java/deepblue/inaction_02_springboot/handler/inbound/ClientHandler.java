package deepblue.inaction_02_springboot.handler.inbound;

import deepblue.inaction_02_springboot.model.Message;
import deepblue.inaction_02_springboot.model.MessageType;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.timeout.IdleState;
import io.netty.handler.timeout.IdleStateEvent;

/**
 * 客户端 Handler（MineNettyClient 使用，不是 Spring Bean）：
 * 打印收到的消息、写空闲时发心跳、断线时回调重连（被踢下线时不重连）。
 * 每个连接 new 一个实例，所以 kicked 状态只属于当前连接。
 */
public class ClientHandler extends SimpleChannelInboundHandler<Message> {

    private final String userId;
    private final Runnable onDisconnected;
    private boolean kicked;

    public ClientHandler(String userId, Runnable onDisconnected) {
        this.userId = userId;
        this.onDisconnected = onDisconnected;
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, Message msg) {
        if (msg.getType() == MessageType.KICKED) {
            kicked = true;
        }
        if (msg.getType() != MessageType.PONG) {
            System.out.println("<< " + msg);
        }
    }

    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
        if (evt instanceof IdleStateEvent e && e.state() == IdleState.WRITER_IDLE) {
            // ⑥ 写空闲：一段时间没发数据，主动发心跳，防止被服务端的读空闲踢掉。
            ctx.writeAndFlush(new Message(MessageType.PING, userId, null, null));
        } else {
            super.userEventTriggered(ctx, evt);
        }
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) {
        if (kicked) {
            // ⑥ 被踢下线是服务端的主动决定，重连只会把对方再踢掉，所以不重连。
            System.out.println("kicked offline, will not reconnect (type quit to exit)");
            return;
        }
        System.out.println("disconnected");
        onDisconnected.run();
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        cause.printStackTrace();
        ctx.close();
    }
}
