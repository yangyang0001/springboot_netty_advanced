package deepblue.inaction_03_app.handler.inbound;

import com.alibaba.fastjson.JSON;
import deepblue.inaction_03_app.model.AppMessage;
import deepblue.inaction_03_app.model.AppMessageType;
import deepblue.inaction_03_app.session.AppSessionManager;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.timeout.IdleStateEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * App 服务端业务 Handler：处理逻辑和 inaction_02 的 ServerBusinessHandler 相同，
 * 区别是登录成功后多下发一条 ONLINE_LIST，上线 / 下线由 AppSessionManager 广播。
 * Spring 单例、无状态（连接状态放在 Channel 的 Attribute 里），所以可以 @Sharable；跑在 bizGroup 业务线程上。
 */
@Slf4j
@Component
@ChannelHandler.Sharable
@RequiredArgsConstructor
public class AppServerHandler extends SimpleChannelInboundHandler<AppMessage> {

    private final AppSessionManager sessionManager;

    @Override
    public void channelActive(ChannelHandlerContext ctx) {
        log.info("AppServerHandler channelActive invoke, userId={}, {} connected: {}", userId(ctx), channelId(ctx), ctx.channel().remoteAddress());
        ctx.fireChannelActive();
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) {
        log.info("AppServerHandler channelInactive invoke, userId={}, {} disconnected: {}", userId(ctx), channelId(ctx), ctx.channel().remoteAddress());
        sessionManager.unbind(ctx.channel());
        ctx.fireChannelInactive();
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, AppMessage msg) {
        String userId = ctx.channel().attr(AppSessionManager.USER_ID).get();
        if (msg.getType() != null && msg.getType().isHeartbeat()) {
            log.debug("AppServerHandler channelRead0 invoke, userId={}, {} recv msg={}", userId(ctx), channelId(ctx), msg);
        } else {
            log.info("AppServerHandler channelRead0 invoke, userId={}, {} recv msg={}", userId(ctx), channelId(ctx), msg);
        }
        if (msg.getType() == null) {
            log.warn("AppServerHandler channelRead0 invoke, userId={}, {} reject: type is required, msg={}", userId(ctx), channelId(ctx), msg);
            reply(ctx, AppMessage.of(AppMessageType.ERROR, "type is required"));
            return;
        }
        switch (msg.getType()) {
            case LOGIN -> {
                if (msg.getFrom() == null || msg.getFrom().isBlank()) {
                    log.warn("AppServerHandler channelRead0 invoke, userId={}, {} login rejected: from is required", userId(ctx), channelId(ctx));
                    reply(ctx, AppMessage.of(AppMessageType.ERROR, "from is required"));
                    return;
                }
                // bind 内部会给其他人广播 USER_ONLINE。
                sessionManager.bind(msg.getFrom(), ctx.channel());
                log.info("AppServerHandler channelRead0 invoke, userId={}, {} login ok", userId(ctx), channelId(ctx));
                reply(ctx, AppMessage.of(AppMessageType.LOGIN_OK, "welcome " + msg.getFrom()));
                // 同一个 ctx、同一个线程先后写出，客户端一定先收到 LOGIN_OK 再收到 ONLINE_LIST。
                reply(ctx, AppMessage.of(AppMessageType.ONLINE_LIST, JSON.toJSONString(sessionManager.onlineUsersExcept(msg.getFrom()))));
            }
            case PING -> reply(ctx, AppMessage.of(AppMessageType.PONG, null));
            case CHAT -> {
                if (userId == null) {
                    log.warn("AppServerHandler channelRead0 invoke, userId={}, {} chat rejected: not logged in", userId(ctx), channelId(ctx));
                    reply(ctx, AppMessage.of(AppMessageType.ERROR, "login first"));
                    return;
                }
                msg.setFrom(userId);
                log.info("AppServerHandler channelRead0 invoke, userId={}, {} chat -> {}: {}", userId(ctx), channelId(ctx), msg.getTo(), msg.getContent());
                if (!sessionManager.send(msg.getTo(), msg)) {
                    log.info("AppServerHandler channelRead0 invoke, userId={}, {} chat -> {} failed: target offline", userId(ctx), channelId(ctx), msg.getTo());
                    reply(ctx, AppMessage.of(AppMessageType.ERROR, msg.getTo() + " is offline"));
                }
            }
            default -> {
                log.warn("AppServerHandler channelRead0 invoke, userId={}, {} unsupported type {}", userId(ctx), channelId(ctx), msg.getType());
                reply(ctx, AppMessage.of(AppMessageType.ERROR, "unsupported type " + msg.getType()));
            }
        }
    }

    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
        if (evt instanceof IdleStateEvent) {
            // ⑥ 读空闲：客户端既没发业务数据也没发心跳，认为已经失联，踢掉。
            log.info("AppServerHandler userEventTriggered invoke, userId={}, {} reader idle, close: {}", userId(ctx), channelId(ctx), ctx.channel().remoteAddress());
            ctx.close();
        } else {
            super.userEventTriggered(ctx, evt);
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        // ② 最后一个入站 Handler 兜底处理异常，否则会传到 Tail 只打一条 warn。
        log.error("AppServerHandler exceptionCaught invoke, userId={}, {} channel error: {}", userId(ctx), channelId(ctx), ctx.channel().remoteAddress(), cause);
        ctx.close();
    }

    private void reply(ChannelHandlerContext ctx, AppMessage message) {
        if (message.getType().isHeartbeat()) {
            log.debug("AppServerHandler reply invoke, userId={}, {} reply {}", userId(ctx), channelId(ctx), message);
        } else {
            log.info("AppServerHandler reply invoke, userId={}, {} reply {}", userId(ctx), channelId(ctx), message);
        }
        // ⑤ 写失败时记录原因并关闭连接。
        ctx.writeAndFlush(message).addListener((ChannelFuture f) -> {
            if (!f.isSuccess()) {
                log.warn("AppServerHandler reply invoke, userId={}, {} reply failed, close: {}", userId(ctx), channelId(ctx), message, f.cause());
                f.channel().close();
            }
        });
    }

    private static String channelId(ChannelHandlerContext ctx) {
        return ctx.channel().id().asShortText();
    }

    private static String userId(ChannelHandlerContext ctx) {
        return AppSessionManager.userId(ctx.channel());
    }
}
