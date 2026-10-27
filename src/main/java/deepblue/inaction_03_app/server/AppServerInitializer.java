package deepblue.inaction_03_app.server;

import deepblue.inaction_03_app.config.AppServerConfig;
import deepblue.inaction_03_app.handler.inbound.AppJsonDecoder;
import deepblue.inaction_03_app.handler.inbound.AppServerHandler;
import deepblue.inaction_03_app.handler.outbound.AppJsonEncoder;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.codec.LineBasedFrameDecoder;
import io.netty.handler.codec.string.StringDecoder;
import io.netty.handler.codec.string.StringEncoder;
import io.netty.handler.timeout.IdleStateHandler;
import io.netty.util.concurrent.EventExecutorGroup;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

/**
 * ② App 服务端每个连接的 Pipeline，结构和 inaction_02 的 NettyServerInitializer 相同：
 * Head → idle → frameDecoder → stringDecoder → jsonDecoder → stringEncoder → jsonEncoder → [bizGroup] business → Tail
 * bizGroup 由 AppNettyServer 随生命周期创建和关闭，所以这里不是 Spring Bean。
 */
public class AppServerInitializer extends ChannelInitializer<SocketChannel> {

    private final AppServerConfig config;
    private final AppJsonDecoder jsonDecoder;
    private final AppJsonEncoder jsonEncoder;
    private final AppServerHandler businessHandler;
    private final EventExecutorGroup bizGroup;

    public AppServerInitializer(AppServerConfig config, AppJsonDecoder jsonDecoder, AppJsonEncoder jsonEncoder,
                                AppServerHandler businessHandler, EventExecutorGroup bizGroup) {
        this.config = config;
        this.jsonDecoder = jsonDecoder;
        this.jsonEncoder = jsonEncoder;
        this.businessHandler = businessHandler;
        this.bizGroup = bizGroup;
    }

    @Override
    protected void initChannel(SocketChannel ch) {
        ChannelPipeline pipeline = ch.pipeline();
        pipeline.addLast("idle", new IdleStateHandler(config.getReaderIdleSeconds(), 0, 0, TimeUnit.SECONDS));
        pipeline.addLast("frameDecoder", new LineBasedFrameDecoder(config.getMaxFrameLength()));
        pipeline.addLast("stringDecoder", new StringDecoder(StandardCharsets.UTF_8));
        pipeline.addLast("jsonDecoder", jsonDecoder);
        pipeline.addLast("stringEncoder", new StringEncoder(StandardCharsets.UTF_8));
        pipeline.addLast("jsonEncoder", jsonEncoder);
        pipeline.addLast(bizGroup, "business", businessHandler);
    }
}
