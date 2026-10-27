package deepblue.inaction_02_springboot.handler.outbound;

import com.alibaba.fastjson.JSON;
import deepblue.inaction_02_springboot.model.Message;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToMessageEncoder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * ④ 出站：Message → 一行 JSON（以 \n 结尾），再交给前面的 StringEncoder 转成 ByteBuf。
 * 无状态，所以可以 @Sharable，作为 Spring 单例被所有 Channel 共用。
 */
@Slf4j
@Component
@ChannelHandler.Sharable
public class JsonMessageEncoder extends MessageToMessageEncoder<Message> {

    @Override
    protected void encode(ChannelHandlerContext ctx, Message msg, List<Object> out) {
        String json = JSON.toJSONString(msg);
        log.info("JsonMessageEncoder encode invoke, [{}] encode -> {}", ctx.channel().id().asShortText(), json);
        out.add(json + "\n");
    }
}
