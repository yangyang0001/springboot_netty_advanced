package deepblue.inaction_02_springboot.client;

import deepblue.inaction_02_springboot.handler.inbound.ClientHandler;
import deepblue.inaction_02_springboot.handler.inbound.JsonMessageDecoder;
import deepblue.inaction_02_springboot.handler.outbound.JsonMessageEncoder;
import deepblue.inaction_02_springboot.session.ChannelSessionManager;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.codec.LineBasedFrameDecoder;
import io.netty.handler.codec.string.StringDecoder;
import io.netty.handler.codec.string.StringEncoder;
import io.netty.handler.timeout.IdleStateHandler;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

/**
 * ② 客户端 Pipeline 组装，和服务端对称。
 * 写空闲 20 秒发心跳，小于服务端 60 秒的读空闲。
 */
public class NettyClientInitializer extends ChannelInitializer<SocketChannel> {

    private final JsonMessageDecoder jsonMessageDecoder = new JsonMessageDecoder();
    private final JsonMessageEncoder jsonMessageEncoder = new JsonMessageEncoder();
    private final String userId;
    private final Runnable onDisconnected;

    public NettyClientInitializer(String userId, Runnable onDisconnected) {
        this.userId = userId;
        this.onDisconnected = onDisconnected;
    }

    @Override
    protected void initChannel(SocketChannel ch) {
        // 客户端也把 userId 绑到 Channel 上，共用的编解码器打日志时能取到。
        ch.attr(ChannelSessionManager.USER_ID).set(userId);
        ChannelPipeline pipeline = ch.pipeline();
        pipeline.addLast("idle", new IdleStateHandler(0, 20, 0, TimeUnit.SECONDS));
        pipeline.addLast("frameDecoder", new LineBasedFrameDecoder(8192));
        pipeline.addLast("stringDecoder", new StringDecoder(StandardCharsets.UTF_8));
        pipeline.addLast("jsonDecoder", jsonMessageDecoder);
        pipeline.addLast("stringEncoder", new StringEncoder(StandardCharsets.UTF_8));
        pipeline.addLast("jsonEncoder", jsonMessageEncoder);
        pipeline.addLast("client", new ClientHandler(userId, onDisconnected));
    }
}
