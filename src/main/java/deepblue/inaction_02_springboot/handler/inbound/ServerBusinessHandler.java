package deepblue.inaction_02_springboot.handler.inbound;

import deepblue.inaction_02_springboot.model.Message;
import deepblue.inaction_02_springboot.model.MessageType;
import deepblue.inaction_02_springboot.session.ChannelSessionManager;
import io.netty.channel.ChannelFuture;
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
 * 日志统一以 [channelId] 开头，结合线程名（nioEventLoopGroup / defaultEventExecutorGroup）追踪一条消息的完整链路。
 */
@Slf4j
@Component
@ChannelHandler.Sharable
@RequiredArgsConstructor
public class ServerBusinessHandler extends SimpleChannelInboundHandler<Message> {

    private final ChannelSessionManager sessionManager;

    @Override
    public void channelActive(ChannelHandlerContext ctx) {
        log.info("ServerBusinessHandler channelActive invoke, [{}] connected: {}", channelId(ctx), ctx.channel().remoteAddress());
        ctx.fireChannelActive();
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) {
        log.info("ServerBusinessHandler channelInactive invoke, [{}] disconnected: {} user={}", channelId(ctx), ctx.channel().remoteAddress(),
                ctx.channel().attr(ChannelSessionManager.USER_ID).get());
        sessionManager.unbind(ctx.channel());
        ctx.fireChannelInactive();
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, Message msg) {
        // ③ SimpleChannelInboundHandler 会在方法结束后自动 release 原始消息。
        String userId = ctx.channel().attr(ChannelSessionManager.USER_ID).get();
        log.info("ServerBusinessHandler channelRead0 invoke, [{}] recv user={} msg={}", channelId(ctx), userId, msg);
        if (msg.getType() == null) {
            log.warn("ServerBusinessHandler channelRead0 invoke, [{}] reject: type is required, msg={}", channelId(ctx), msg);
            reply(ctx, Message.of(MessageType.ERROR, "type is required"));
            return;
        }
        switch (msg.getType()) {
            case LOGIN -> {
                if (msg.getFrom() == null || msg.getFrom().isBlank()) {
                    log.warn("ServerBusinessHandler channelRead0 invoke, [{}] login rejected: from is required", channelId(ctx));
                    reply(ctx, Message.of(MessageType.ERROR, "from is required"));
                    return;
                }
                // 真实项目这里通常是查库 / 校验 token，是阻塞操作，所以业务 Handler 要放在业务线程池。
                sessionManager.bind(msg.getFrom(), ctx.channel());
                log.info("ServerBusinessHandler channelRead0 invoke, [{}] login: {}", channelId(ctx), msg.getFrom());
                reply(ctx, Message.of(MessageType.LOGIN_OK, "welcome " + msg.getFrom()));
            }
            case PING -> reply(ctx, Message.of(MessageType.PONG, null));
            case CHAT -> {
                if (userId == null) {
                    log.warn("ServerBusinessHandler channelRead0 invoke, [{}] chat rejected: not logged in", channelId(ctx));
                    reply(ctx, Message.of(MessageType.ERROR, "login first"));
                    return;
                }
                msg.setFrom(userId);
                log.info("ServerBusinessHandler channelRead0 invoke, [{}] chat {} -> {}: {}", channelId(ctx), userId, msg.getTo(), msg.getContent());
                if (!sessionManager.send(msg.getTo(), msg)) {
                    log.info("ServerBusinessHandler channelRead0 invoke, [{}] chat {} -> {} failed: target offline", channelId(ctx), userId, msg.getTo());
                    reply(ctx, Message.of(MessageType.ERROR, msg.getTo() + " is offline"));
                }
            }
            default -> {
                log.warn("ServerBusinessHandler channelRead0 invoke, [{}] unsupported type {}", channelId(ctx), msg.getType());
                reply(ctx, Message.of(MessageType.ERROR, "unsupported type " + msg.getType()));
            }
        }
    }

    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
        if (evt instanceof IdleStateEvent) {
            // ⑥ 读空闲：客户端既没发业务数据也没发心跳，认为已经失联，踢掉。
            log.info("ServerBusinessHandler userEventTriggered invoke, [{}] reader idle, close: {} user={}", channelId(ctx), ctx.channel().remoteAddress(),
                    ctx.channel().attr(ChannelSessionManager.USER_ID).get());
            ctx.close();
        } else {
            super.userEventTriggered(ctx, evt);
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        // ② 最后一个入站 Handler 兜底处理异常，否则会传到 Tail 只打一条 warn。
        log.error("ServerBusinessHandler exceptionCaught invoke, [{}] channel error: {} user={}", channelId(ctx), ctx.channel().remoteAddress(),
                ctx.channel().attr(ChannelSessionManager.USER_ID).get(), cause);
        ctx.close();
    }

    private void reply(ChannelHandlerContext ctx, Message message) {
        log.info("ServerBusinessHandler reply invoke, [{}] reply {}", channelId(ctx), message);
        // ⑤ 写失败时记录原因并关闭连接。
        ctx.writeAndFlush(message).addListener((ChannelFuture f) -> {
            if (!f.isSuccess()) {
                log.warn("ServerBusinessHandler reply invoke, [{}] reply failed, close: {}", channelId(ctx), message, f.cause());
                f.channel().close();
            }
        });
    }

    private static String channelId(ChannelHandlerContext ctx) {
        return ctx.channel().id().asShortText();
    }
}
