package deepblue.inaction_02_springboot.client;

import deepblue.inaction_02_springboot.model.Message;
import deepblue.inaction_02_springboot.model.MessageType;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioSocketChannel;

import java.nio.charset.StandardCharsets;
import java.util.Scanner;
import java.util.concurrent.TimeUnit;

/**
 * 客户端演示，不依赖 Spring，直接运行 main：
 * 先启动 NettyAdvancedApplication，再启动两个客户端（程序参数分别为 tom、jerry；可选第二个参数为端口，默认 9000）。
 * 控制台输入 "jerry hello" 表示给 jerry 发 hello。
 *
 * 演示：写空闲发心跳、断线指数退避重连（关掉服务端再启动即可观察）。
 */
public final class NettyClientDemo {

    private static final String HOST = "127.0.0.1";
    private static final int DEFAULT_PORT = 9000;
    private static final int MAX_RECONNECT_DELAY_SECONDS = 30;

    private final String userId;
    private final int port;
    private final EventLoopGroup group = new NioEventLoopGroup(1);
    private final Bootstrap bootstrap = new Bootstrap();
    private volatile Channel channel;
    private int reconnectDelaySeconds = 1;

    private NettyClientDemo(String userId, int port) {
        this.userId = userId;
        this.port = port;
        bootstrap.group(group)
                .channel(NioSocketChannel.class)
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 3000)
                .option(ChannelOption.TCP_NODELAY, true)
                .handler(new NettyClientInitializer(userId, this::onDisconnected));
    }

    public static void main(String[] args) {
        NettyClientDemo client = new NettyClientDemo(args.length > 0 ? args[0] : "tom",
                args.length > 1 ? Integer.parseInt(args[1]) : DEFAULT_PORT);
        client.connect();
        client.readConsole();
    }

    private void connect() {
        ChannelFuture future = bootstrap.connect(HOST, port);
        future.addListener((ChannelFuture f) -> {
            if (f.isSuccess()) {
                reconnectDelaySeconds = 1;
                channel = f.channel();
                channel.writeAndFlush(new Message(MessageType.LOGIN, userId, null, null));
            } else {
                scheduleReconnect();
            }
        });
    }

    /** ⑥ 指数退避重连：1, 2, 4 ... 最多 30 秒。 */
    private void scheduleReconnect() {
        int delay = reconnectDelaySeconds;
        reconnectDelaySeconds = Math.min(reconnectDelaySeconds * 2, MAX_RECONNECT_DELAY_SECONDS);
        System.out.println("reconnect in " + delay + "s");
        group.schedule(this::connect, delay, TimeUnit.SECONDS);
    }

    private void onDisconnected() {
        if (!group.isShuttingDown()) {
            scheduleReconnect();
        }
    }

    private void readConsole() {
        Scanner scanner = new Scanner(System.in, StandardCharsets.UTF_8);
        while (scanner.hasNextLine()) {
            String line = scanner.nextLine().trim();
            if ("quit".equals(line)) {
                break;
            }
            String[] parts = line.split("\\s+", 2);
            Channel ch = channel;
            if (parts.length == 2 && ch != null && ch.isActive()) {
                ch.writeAndFlush(new Message(MessageType.CHAT, userId, parts[0], parts[1]));
            } else {
                System.out.println("usage: <to> <content>");
            }
        }
        group.shutdownGracefully();
    }
}
