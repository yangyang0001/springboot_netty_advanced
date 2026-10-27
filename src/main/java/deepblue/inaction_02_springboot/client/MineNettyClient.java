package deepblue.inaction_02_springboot.client;

import deepblue.inaction_02_springboot.model.Message;
import deepblue.inaction_02_springboot.model.MessageEnum;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioSocketChannel;
import lombok.extern.slf4j.Slf4j;

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
@Slf4j
public final class MineNettyClient {

    private static final String HOST = "127.0.0.1";
    private static final int DEFAULT_PORT = 9000;
    private static final int MAX_RECONNECT_DELAY_SECONDS = 30;

    private final String userId;
    private final int port;
    private final EventLoopGroup group = new NioEventLoopGroup(1);
    private final Bootstrap bootstrap = new Bootstrap();
    private volatile Channel channel;
    private int reconnectDelaySeconds = 1;

    private MineNettyClient(String userId, int port) {
        this.userId = userId;
        this.port = port;
        bootstrap.group(group)
                .channel(NioSocketChannel.class)
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 3000)
                .option(ChannelOption.TCP_NODELAY, true)
                .handler(new NettyClientInitializer(userId, this::onDisconnected));
    }

    public static void main(String[] args) {
        MineNettyClient client = new MineNettyClient(args.length > 0 ? args[0] : "tom",
                args.length > 1 ? Integer.parseInt(args[1]) : DEFAULT_PORT);
        client.connect();
        client.readConsole();
    }

    private void connect() {
        log.info("MineNettyClient connect invoke, {} connecting to {}:{}", userId, HOST, port);
        ChannelFuture future = bootstrap.connect(HOST, port);
        future.addListener((ChannelFuture f) -> {
            if (f.isSuccess()) {
                reconnectDelaySeconds = 1;
                channel = f.channel();
                log.info("MineNettyClient connect invoke, [{}] connect ok, send LOGIN as {}", channel.id().asShortText(), userId);
                send(channel, new Message(MessageEnum.LOGIN, userId, null, null));
            } else {
                log.warn("MineNettyClient connect invoke, connect to {}:{} failed: {}", HOST, port, f.cause().toString());
                scheduleReconnect();
            }
        });
    }

    /** ⑥ 指数退避重连：1, 2, 4 ... 最多 30 秒。 */
    private void scheduleReconnect() {
        int delay = reconnectDelaySeconds;
        reconnectDelaySeconds = Math.min(reconnectDelaySeconds * 2, MAX_RECONNECT_DELAY_SECONDS);
        log.info("MineNettyClient scheduleReconnect invoke, reconnect in {}s", delay);
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
                log.info("MineNettyClient readConsole invoke, [{}] >> CHAT to={} content={}", ch.id().asShortText(), parts[0], parts[1]);
                send(ch, new Message(MessageEnum.CHAT, userId, parts[0], parts[1]));
            } else {
                System.out.println("usage: <to> <content>");
            }
        }
        log.info("MineNettyClient readConsole invoke, {} quit, shutting down", userId);
        group.shutdownGracefully();
    }

    private static void send(Channel ch, Message message) {
        ch.writeAndFlush(message).addListener((ChannelFuture f) -> {
            if (!f.isSuccess()) {
                log.warn("MineNettyClient send invoke, [{}] send failed: {}", ch.id().asShortText(), message, f.cause());
            }
        });
    }
}
