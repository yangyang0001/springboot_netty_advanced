package deepblue.inaction_02_springboot.handler.inbound;

import deepblue.inaction_02_springboot.model.Message;
import deepblue.inaction_02_springboot.model.MessageType;
import deepblue.inaction_02_springboot.session.ChannelSessionManager;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.timeout.IdleStateEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 业务 Handler：Spring 单例，可以直接注入 Service / Mapper 等 Bean。
 * 自身无状态（连接状态放在 Channel 的 Attribute 里），所以可以 @Sharable。
 * 在 NettyServer 中通过 addLast(bizGroup, ...) 跑在业务线程池，阻塞操作不会卡住 IO 线程。
 */
@Slf4j
@Component
@ChannelHandler.Sharable
@RequiredArgsConstructor
public class ServerBusinessHandler extends SimpleChannelInboundHandler<Message> {

    private final ChannelSessionManager sessionManager;

    @Override
    public void channelActive(ChannelHandlerContext ctx) {
        log.info("connected: {}", ctx.channel().remoteAddress());
        ctx.fireChannelActive();
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) {
        log.info("disconnected: {} user={}", ctx.channel().remoteAddress(),
                ctx.channel().attr(ChannelSessionManager.USER_ID).get());
        sessionManager.unbind(ctx.channel());
        ctx.fireChannelInactive();
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, Message msg) {
        // ③ SimpleChannelInboundHandler 会在方法结束后自动 release 原始消息。
        if (msg.getType() == null) {
            reply(ctx, Message.of(MessageType.ERROR, "type is required"));
            return;
        }
        String userId = ctx.channel().attr(ChannelSessionManager.USER_ID).get();
        switch (msg.getType()) {
            case LOGIN -> {
                if (msg.getFrom() == null || msg.getFrom().isBlank()) {
                    reply(ctx, Message.of(MessageType.ERROR, "from is required"));
                    return;
                }
                // 真实项目这里通常是查库 / 校验 token，是阻塞操作，所以业务 Handler 要放在业务线程池。
                sessionManager.bind(msg.getFrom(), ctx.channel());
                log.info("login: {}", msg.getFrom());
                reply(ctx, Message.of(MessageType.LOGIN_OK, "welcome " + msg.getFrom()));
            }
            case PING -> reply(ctx, Message.of(MessageType.PONG, null));
            case CHAT -> {
                if (userId == null) {
                    reply(ctx, Message.of(MessageType.ERROR, "login first"));
                    return;
                }
                msg.setFrom(userId);
                if (!sessionManager.send(msg.getTo(), msg)) {
                    reply(ctx, Message.of(MessageType.ERROR, msg.getTo() + " is offline"));
                }
            }
            default -> reply(ctx, Message.of(MessageType.ERROR, "unsupported type " + msg.getType()));
        }
    }

    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
        if (evt instanceof IdleStateEvent) {
            // ⑥ 读空闲：客户端既没发业务数据也没发心跳，认为已经失联，踢掉。
            log.info("reader idle, close: {}", ctx.channel().remoteAddress());
            ctx.close();
        } else {
            super.userEventTriggered(ctx, evt);
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        // ② 最后一个入站 Handler 兜底处理异常，否则会传到 Tail 只打一条 warn。
        log.error("channel error: {}", ctx.channel().remoteAddress(), cause);
        ctx.close();
    }

    private void reply(ChannelHandlerContext ctx, Message message) {
        // ⑤ 写失败时关闭连接。
        ctx.writeAndFlush(message).addListener(ChannelFutureListener.CLOSE_ON_FAILURE);
    }
}
