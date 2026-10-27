package deepblue.inaction_03_app.handler.outbound;

import com.alibaba.fastjson.JSON;
import deepblue.inaction_03_app.model.AppMessage;
import deepblue.inaction_03_app.session.AppSessionManager;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToMessageEncoder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * ④ 出站：AppMessage → 一行 JSON（以 \n 结尾），再交给前面的 StringEncoder 转成 ByteBuf。服务端和 App 客户端共用。
 * 无状态，所以可以 @Sharable，作为 Spring 单例被所有 Channel 共用。
 */
@Slf4j
@Component
@ChannelHandler.Sharable
public class AppJsonEncoder extends MessageToMessageEncoder<AppMessage> {

    @Override
    protected void encode(ChannelHandlerContext ctx, AppMessage msg, List<Object> out) {
        String json = JSON.toJSONString(msg);
        if (msg.getType() != null && msg.getType().isHeartbeat()) {
            log.debug("AppJsonEncoder encode invoke, userId={}, {} encode -> {}", AppSessionManager.userId(ctx.channel()), ctx.channel().id().asShortText(), json);
        } else {
            log.info("AppJsonEncoder encode invoke, userId={}, {} encode -> {}", AppSessionManager.userId(ctx.channel()), ctx.channel().id().asShortText(), json);
        }
        out.add(json + "\n");
    }
}
