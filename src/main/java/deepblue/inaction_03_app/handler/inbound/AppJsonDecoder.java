package deepblue.inaction_03_app.handler.inbound;

import com.alibaba.fastjson.JSON;
import deepblue.inaction_03_app.model.AppMessage;
import deepblue.inaction_03_app.session.AppSessionManager;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToMessageDecoder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * ④ 入站：String → AppMessage（JSON）。服务端和 App 客户端共用。
 * 无状态，所以可以 @Sharable，作为 Spring 单例被所有 Channel 共用。
 */
@Slf4j
@Component
@ChannelHandler.Sharable
public class AppJsonDecoder extends MessageToMessageDecoder<String> {

    @Override
    protected void decode(ChannelHandlerContext ctx, String line, List<Object> out) {
        if (line.isBlank()) {
            return;
        }
        AppMessage message;
        try {
            message = JSON.parseObject(line, AppMessage.class);
        } catch (RuntimeException e) {
            // 解析失败继续抛出，交给最后一个入站 Handler 的 exceptionCaught，这里先记下原始报文。
            log.warn("AppJsonDecoder decode invoke, userId={}, {} decode failed <- {}", AppSessionManager.userId(ctx.channel()), ctx.channel().id().asShortText(), line);
            throw e;
        }
        if (message.getType() != null && message.getType().isHeartbeat()) {
            log.debug("AppJsonDecoder decode invoke, userId={}, {} decode <- {}", AppSessionManager.userId(ctx.channel()), ctx.channel().id().asShortText(), line);
        } else {
            log.info("AppJsonDecoder decode invoke, userId={}, {} decode <- {}", AppSessionManager.userId(ctx.channel()), ctx.channel().id().asShortText(), line);
        }
        out.add(message);
    }
}
