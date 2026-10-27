package deepblue.inaction_03_app.client;

import deepblue.inaction_03_app.model.AppMessage;
import deepblue.inaction_03_app.model.AppMessageType;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioSocketChannel;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.TimeUnit;

/**
 * 与界面无关的 Netty 聊天客户端：连接、自动登录、发送、断线指数退避重连、关闭。
 * 界面层（MineNettyApp）只负责展示，通过 ClientListener 接收事件。
 *
 * 线程说明：
 * - connect / 重连 / 回调都在唯一的 EventLoop 线程上执行，所以 reconnectDelaySeconds 不需要加锁；
 * - send 可以在任意线程（例如 Swing 界面线程）调用，Netty 会把写操作交回 EventLoop 执行。
 */
@Slf4j
public class NettyChatClient {

    private static final int MAX_RECONNECT_DELAY_SECONDS = 30;

    @Getter
    private final String userId;
    @Getter
    private final String host;
    @Getter
    private final int port;
    private final ClientListener listener;
    private final EventLoopGroup group = new NioEventLoopGroup(1);
    private final Bootstrap bootstrap = new Bootstrap();
    private volatile Channel channel;
    private volatile boolean closed;
    private int reconnectDelaySeconds = 1;

    public NettyChatClient(String userId, String host, int port, ClientListener listener) {
        this.userId = userId;
        this.host = host;
        this.port = port;
        this.listener = listener;
        bootstrap.group(group)
                .channel(NioSocketChannel.class)
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 3000)
                .option(ChannelOption.TCP_NODELAY, true)
                .handler(new AppClientInitializer(userId, listener, this::scheduleReconnect));
    }

    public void start() {
        log.info("NettyChatClient start invoke, userId={}, client={}", userId, this);
        connect();
    }

    public boolean isConnected() {
        Channel ch = channel;
        return ch != null && ch.isActive();
    }

    /**
     * 发送一条消息，返回写操作的 Future；未连接时返回 null。
     * ⑤ 写是异步的：返回时数据还没写出，调用方用 addListener 处理结果，不要在界面线程 sync() 等待。
     */
    public ChannelFuture send(AppMessage message) {
        Channel ch = channel;
        if (ch == null || !ch.isActive()) {
            log.warn("NettyChatClient send invoke, userId={}, not connected, drop: {}", userId, message);
            return null;
        }
        log.info("NettyChatClient send invoke, userId={}, {} >> {}", userId, ch.id().asShortText(), message);
        ChannelFuture future = ch.writeAndFlush(message);
        future.addListener((ChannelFuture f) -> {
            if (!f.isSuccess()) {
                log.warn("NettyChatClient send invoke, userId={}, {} send failed: {}", userId, ch.id().asShortText(), message, f.cause());
            }
        });
        return future;
    }

    /** 关闭连接并停止重连，EventLoop 线程退出后 JVM 才能正常结束。 */
    public void close() {
        log.info("NettyChatClient close invoke, userId={}, closing", userId);
        closed = true;
        Channel ch = channel;
        if (ch != null) {
            ch.close();
        }
        group.shutdownGracefully(0, 2, TimeUnit.SECONDS);
    }

    @Override
    public String toString() {
        return "NettyChatClient{userId=" + userId + ", server=" + host + ":" + port + "}";
    }

    private void connect() {
        if (closed) {
            return;
        }
        log.info("NettyChatClient connect invoke, userId={}, connecting to {}:{}", userId, host, port);
        bootstrap.connect(host, port).addListener((ChannelFuture f) -> {
            if (f.isSuccess()) {
                reconnectDelaySeconds = 1;
                channel = f.channel();
                log.info("NettyChatClient connect invoke, userId={}, {} connect ok, send LOGIN", userId, channel.id().asShortText());
                listener.onConnected();
                send(new AppMessage(AppMessageType.LOGIN, userId, null, null));
            } else {
                log.warn("NettyChatClient connect invoke, userId={}, connect to {}:{} failed: {}", userId, host, port, f.cause().toString());
                scheduleReconnect();
            }
        });
    }

    /** ⑥ 指数退避重连：1, 2, 4 ... 最多 30 秒；关闭后不再重连。 */
    private void scheduleReconnect() {
        if (closed || group.isShuttingDown()) {
            return;
        }
        int delay = reconnectDelaySeconds;
        reconnectDelaySeconds = Math.min(reconnectDelaySeconds * 2, MAX_RECONNECT_DELAY_SECONDS);
        log.info("NettyChatClient scheduleReconnect invoke, userId={}, reconnect in {}s", userId, delay);
        listener.onReconnecting(delay);
        group.schedule(this::connect, delay, TimeUnit.SECONDS);
    }
}
