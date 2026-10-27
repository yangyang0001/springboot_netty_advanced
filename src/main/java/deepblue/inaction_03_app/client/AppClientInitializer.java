package deepblue.inaction_03_app.client;

import deepblue.inaction_03_app.handler.inbound.AppClientHandler;
import deepblue.inaction_03_app.handler.inbound.AppJsonDecoder;
import deepblue.inaction_03_app.handler.outbound.AppJsonEncoder;
import deepblue.inaction_03_app.session.AppSessionManager;
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
 * ② App 客户端 Pipeline 组装：结构和服务端 AppServerInitializer 对称，
 * 与服务端共用 AppJsonDecoder / AppJsonEncoder，最后一站是 AppClientHandler。
 * 写空闲 20 秒发心跳，小于服务端 60 秒的读空闲。
 */
public class AppClientInitializer extends ChannelInitializer<SocketChannel> {

    private final AppJsonDecoder jsonDecoder = new AppJsonDecoder();
    private final AppJsonEncoder jsonEncoder = new AppJsonEncoder();
    private final String userId;
    private final ClientListener listener;
    private final Runnable onDisconnected;

    public AppClientInitializer(String userId, ClientListener listener, Runnable onDisconnected) {
        this.userId = userId;
        this.listener = listener;
        this.onDisconnected = onDisconnected;
    }

    @Override
    protected void initChannel(SocketChannel ch) {
        // 把 userId 绑到 Channel 上，共用的编解码器打日志时能取到。
        ch.attr(AppSessionManager.USER_ID).set(userId);
        ChannelPipeline pipeline = ch.pipeline();
        pipeline.addLast("idle", new IdleStateHandler(0, 20, 0, TimeUnit.SECONDS));
        pipeline.addLast("frameDecoder", new LineBasedFrameDecoder(8192));
        pipeline.addLast("stringDecoder", new StringDecoder(StandardCharsets.UTF_8));
        pipeline.addLast("jsonDecoder", jsonDecoder);
        pipeline.addLast("stringEncoder", new StringEncoder(StandardCharsets.UTF_8));
        pipeline.addLast("jsonEncoder", jsonEncoder);
        pipeline.addLast("client", new AppClientHandler(userId, listener, onDisconnected));
    }
}
