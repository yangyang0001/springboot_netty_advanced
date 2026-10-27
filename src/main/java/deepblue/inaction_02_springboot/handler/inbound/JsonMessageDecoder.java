package deepblue.inaction_02_springboot.handler.inbound;

import com.alibaba.fastjson.JSON;
import deepblue.inaction_02_springboot.model.Message;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToMessageDecoder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * ④ 入站：String → Message（JSON）。
 * 无状态，所以可以 @Sharable，作为 Spring 单例被所有 Channel 共用。
 * 帧边界（按行切分）和 ByteBuf → String 交给前面的 LineBasedFrameDecoder / StringDecoder。
 */
@Slf4j
@Component
@ChannelHandler.Sharable
public class JsonMessageDecoder extends MessageToMessageDecoder<String> {

    @Override
    protected void decode(ChannelHandlerContext ctx, String line, List<Object> out) {
        if (line.isBlank()) {
            log.info("JsonMessageDecoder decode invoke, [{}] decode skip blank line", ctx.channel().id().asShortText());
            return;
        }
        // 解析失败会抛异常，沿 Pipeline 传到业务 Handler 的 exceptionCaught，这里先记下原始报文。
        log.info("JsonMessageDecoder decode invoke, [{}] decode <- {}", ctx.channel().id().asShortText(), line);
        out.add(JSON.parseObject(line, Message.class));
    }
}
