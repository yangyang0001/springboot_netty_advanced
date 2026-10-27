package deepblue.inaction_02_springboot.server;

import deepblue.inaction_02_springboot.config.NettyServerConfig;
import deepblue.inaction_02_springboot.handler.inbound.JsonMessageDecoder;
import deepblue.inaction_02_springboot.handler.inbound.ServerBusinessHandler;
import deepblue.inaction_02_springboot.handler.outbound.JsonMessageEncoder;
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
 * Netty 服务端与 Spring 生命周期绑定：
 * 容器刷新完成后 start() 绑定端口（此时所有 Bean 已就绪，Handler 可以放心使用注入的依赖）；
 * 容器关闭时 stop() 关闭监听并 shutdownGracefully。
 * 用 SmartLifecycle 而不是 @PostConstruct + sync() 阻塞，避免卡住 Spring 启动线程。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NettyServer implements SmartLifecycle {

    private final NettyServerConfig properties;
    private final JsonMessageDecoder jsonMessageDecoder;
    private final JsonMessageEncoder jsonMessageEncoder;
    private final ServerBusinessHandler businessHandler;

    private EventLoopGroup bossGroup;
    private EventLoopGroup workerGroup;
    private EventExecutorGroup bizGroup;
    private Channel serverChannel;
    private volatile boolean running;

    @Override
    public void start() {
        log.info("NettyServer start invoke, userId=-, Netty server starting: {}", properties);
        // ① 主从 Reactor：boss 负责 accept，worker 负责 IO，biz 负责业务。
        bossGroup = new NioEventLoopGroup(properties.getBossThreads());
        workerGroup = new NioEventLoopGroup(properties.getWorkerThreads());
        bizGroup = new DefaultEventExecutorGroup(properties.getBizThreads());

        ServerBootstrap bootstrap = new ServerBootstrap()
                .group(bossGroup, workerGroup)
                // ⑧ Linux 生产环境可以替换为 EpollServerSocketChannel。
                .channel(NioServerSocketChannel.class)
                // ⑥ 连接参数。
                .option(ChannelOption.SO_BACKLOG, 1024)
                .childOption(ChannelOption.TCP_NODELAY, true)
                .childOption(ChannelOption.SO_KEEPALIVE, true)
                // ⑦ 高低水位：待写字节超过高水位 isWritable() 变为 false，降到低水位以下恢复为 true。
                .childOption(ChannelOption.WRITE_BUFFER_WATER_MARK, new WriteBufferWaterMark(
                        properties.getWriteBufferLowWaterMark(), properties.getWriteBufferHighWaterMark()))
                .childHandler(new NettyServerInitializer(
                        properties, jsonMessageDecoder, jsonMessageEncoder, businessHandler, bizGroup));

        // ⑤ 启动阶段允许 sync：这里是 Spring 启动线程，不是 EventLoop 线程。
        serverChannel = bootstrap.bind(properties.getPort()).syncUninterruptibly().channel();
        running = true;
        log.info("NettyServer start invoke, userId=-, Netty server started on port {}", getPort());
    }

    @Override
    public void stop() {
        log.info("NettyServer stop invoke, userId=-, Netty server stopping");
        running = false;
        if (serverChannel != null) {
            serverChannel.close().syncUninterruptibly();
        }
        // ⑥ 优雅关闭：先停 accept，再停 IO，最后停业务线程。
        shutdown(bossGroup);
        shutdown(workerGroup);
        shutdown(bizGroup);
        log.info("NettyServer stop invoke, userId=-, Netty server stopped");
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
