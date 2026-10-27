package deepblue.inaction_02_springboot.server;

import deepblue.inaction_02_springboot.config.NettyServerConfig;
import deepblue.inaction_02_springboot.handler.inbound.JsonMessageDecoder;
import deepblue.inaction_02_springboot.handler.inbound.ServerBusinessHandler;
import deepblue.inaction_02_springboot.handler.outbound.JsonMessageEncoder;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.codec.LineBasedFrameDecoder;
import io.netty.handler.codec.string.StringDecoder;
import io.netty.handler.codec.string.StringEncoder;
import io.netty.handler.timeout.IdleStateHandler;
import io.netty.util.concurrent.EventExecutorGroup;
import lombok.extern.slf4j.Slf4j;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

/**
 * ② 服务端每个连接的 Pipeline 组装。
 * 有状态的 Handler（IdleStateHandler、FrameDecoder 等）每次 new，@Sharable 的直接复用 Spring 单例。
 * bizGroup 由 NettyServer 随生命周期创建和关闭，所以这里不是 Spring Bean，而是在 start() 中 new 出来。
 */
@Slf4j
public class NettyServerInitializer extends ChannelInitializer<SocketChannel> {

    private final NettyServerConfig config;
    private final JsonMessageDecoder jsonMessageDecoder;
    private final JsonMessageEncoder jsonMessageEncoder;
    private final ServerBusinessHandler businessHandler;
    private final EventExecutorGroup bizGroup;

    public NettyServerInitializer(NettyServerConfig config,
                                  JsonMessageDecoder jsonMessageDecoder,
                                  JsonMessageEncoder jsonMessageEncoder,
                                  ServerBusinessHandler businessHandler,
                                  EventExecutorGroup bizGroup) {
        this.config = config;
        this.jsonMessageDecoder = jsonMessageDecoder;
        this.jsonMessageEncoder = jsonMessageEncoder;
        this.businessHandler = businessHandler;
        this.bizGroup = bizGroup;
    }

    @Override
    protected void initChannel(SocketChannel ch) {
        ChannelPipeline pipeline = ch.pipeline();
        // ⑥ 读空闲检测，触发后由 ServerBusinessHandler 断开连接。
        pipeline.addLast("idle", new IdleStateHandler(config.getReaderIdleSeconds(), 0, 0, TimeUnit.SECONDS));
        // ④ 入站：按 \n 切帧 → ByteBuf 转 String → JSON 转 Message。
        pipeline.addLast("frameDecoder", new LineBasedFrameDecoder(config.getMaxFrameLength()));
        pipeline.addLast("stringDecoder", new StringDecoder(StandardCharsets.UTF_8));
        pipeline.addLast("jsonDecoder", jsonMessageDecoder);
        // ④ 出站：Message 转 JSON → String 转 ByteBuf。编码器必须在业务 Handler 之前。
        pipeline.addLast("stringEncoder", new StringEncoder(StandardCharsets.UTF_8));
        pipeline.addLast("jsonEncoder", jsonMessageEncoder);
        // ① 业务 Handler 跑在 bizGroup 上，同一个 Channel 的事件始终由同一个业务线程处理，顺序不变。
        pipeline.addLast(bizGroup, "business", businessHandler);
        log.info("NettyServerInitializer initChannel invoke, [{}] pipeline initialized: {}", ch.id().asShortText(), pipeline.names());
    }
}
