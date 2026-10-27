package deepblue.inaction_03_app.server;

import deepblue.inaction_03_app.config.AppServerConfig;
import deepblue.inaction_03_app.handler.inbound.AppJsonDecoder;
import deepblue.inaction_03_app.handler.inbound.AppServerHandler;
import deepblue.inaction_03_app.handler.outbound.AppJsonEncoder;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.WriteBufferWaterMark;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.util.concurrent.DefaultEventExecutorGroup;
import io.netty.util.concurrent.EventExecutorGroup;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;

import java.net.InetSocketAddress;
import java.util.concurrent.TimeUnit;

/**
 * App 专用的 Netty 服务端（默认 TCP 9100），和 inaction_02 的 NettyServer（9000）同时运行、互不影响：
 * 各自的线程组、会话表、消息类型。生命周期同样交给 Spring 的 SmartLifecycle 管理。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AppNettyServer implements SmartLifecycle {

    private final AppServerConfig config;
    private final AppJsonDecoder jsonDecoder;
    private final AppJsonEncoder jsonEncoder;
    private final AppServerHandler businessHandler;

    private EventLoopGroup bossGroup;
    private EventLoopGroup workerGroup;
    private EventExecutorGroup bizGroup;
    private Channel serverChannel;
    private volatile boolean running;

    @Override
    public void start() {
        log.info("AppNettyServer start invoke, userId=-, App server starting: {}", config);
        bossGroup = new NioEventLoopGroup(config.getBossThreads());
        workerGroup = new NioEventLoopGroup(config.getWorkerThreads());
        bizGroup = new DefaultEventExecutorGroup(config.getBizThreads());

        ServerBootstrap bootstrap = new ServerBootstrap()
                .group(bossGroup, workerGroup)
                .channel(NioServerSocketChannel.class)
                .option(ChannelOption.SO_BACKLOG, 1024)
                .childOption(ChannelOption.TCP_NODELAY, true)
                .childOption(ChannelOption.SO_KEEPALIVE, true)
                .childOption(ChannelOption.WRITE_BUFFER_WATER_MARK, new WriteBufferWaterMark(
                        config.getWriteBufferLowWaterMark(), config.getWriteBufferHighWaterMark()))
                .childHandler(new AppServerInitializer(config, jsonDecoder, jsonEncoder, businessHandler, bizGroup));

        serverChannel = bootstrap.bind(config.getPort()).syncUninterruptibly().channel();
        running = true;
        log.info("AppNettyServer start invoke, userId=-, App server started on port {}", getPort());
    }

    @Override
    public void stop() {
        log.info("AppNettyServer stop invoke, userId=-, App server stopping");
        running = false;
        if (serverChannel != null) {
            serverChannel.close().syncUninterruptibly();
        }
        shutdown(bossGroup);
        shutdown(workerGroup);
        shutdown(bizGroup);
        log.info("AppNettyServer stop invoke, userId=-, App server stopped");
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    /** 实际绑定的端口（配置为 0 时由系统分配）。 */
    public int getPort() {
        return ((InetSocketAddress) serverChannel.localAddress()).getPort();
    }

    private static void shutdown(EventExecutorGroup group) {
        if (group != null) {
            group.shutdownGracefully(0, 5, TimeUnit.SECONDS).syncUninterruptibly();
        }
    }
}
