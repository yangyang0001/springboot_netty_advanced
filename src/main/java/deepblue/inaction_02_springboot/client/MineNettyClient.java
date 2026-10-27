package deepblue.inaction_02_springboot.client;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONException;
import com.alibaba.fastjson.JSONObject;
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
import java.util.EnumSet;
import java.util.Scanner;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * 客户端演示，不依赖 Spring，直接运行 main：
 * 先启动 NettyAdvancedApplication，再启动两个客户端（程序参数分别为 tom、jerry；可选第二个参数为端口，默认 9000）。
 * 控制台每行输入一条 JSON 消息，和线上传输的格式一致，例如（当前用户为 jerry）：
 *   {"content":"Hello World","to":"tom","type":"CHAT"}
 *   {"type":"PING"}
 * from 不用填，自动填为发送者（当前 userId）。
 * 输入 quit 退出。
 *
 * 演示：写空闲发心跳、断线指数退避重连（关掉服务端再启动即可观察）。
 */
@Slf4j
public final class MineNettyClient {

    private static final String HOST = "127.0.0.1";
    private static final int DEFAULT_PORT = 9000;
    private static final int MAX_RECONNECT_DELAY_SECONDS = 30;
    /** 控制台允许手动发送的类型；LOGIN 由连接成功后自动发送，其余类型只能由服务端发出。 */
    private static final Set<MessageEnum> CONSOLE_TYPES = EnumSet.of(MessageEnum.CHAT, MessageEnum.PING);
    private static final String USAGE = """
            input one JSON message per line, e.g.
              {"content":"Hello World","to":"<other>","type":"CHAT"}
              {"type":"PING"}
            from is filled with the sender automatically
            type quit to exit""";

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
        log.info("MineNettyClient main invoke, client={}", client);
        client.connect();
        client.readConsole();
    }

    /** 只打印关键信息；group、bootstrap、channel 不适合直接打印。 */
    @Override
    public String toString() {
        return "MineNettyClient{userId=" + userId + ", server=" + HOST + ":" + port + "}";
    }

    private void connect() {
        log.info("MineNettyClient connect invoke, {} connecting to {}:{}", userId, HOST, port);
        ChannelFuture future = bootstrap.connect(HOST, port);
        future.addListener((ChannelFuture f) -> {
            if (f.isSuccess()) {
                reconnectDelaySeconds = 1;
                channel = f.channel();
                log.info("MineNettyClient connect invoke, {} connect ok, send LOGIN as {}", channel.id().asShortText(), userId);
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
        System.out.println(USAGE);
        Scanner scanner = new Scanner(System.in, StandardCharsets.UTF_8);
        while (scanner.hasNextLine()) {
            String line = scanner.nextLine().trim();
            if (line.isEmpty()) {
                continue;
            }
            if ("quit".equals(line)) {
                break;
            }
            Message message;
            try {
                message = parseInput(userId, line);
            } catch (IllegalArgumentException e) {
                log.warn("MineNettyClient readConsole invoke, invalid input: {} -> {}", line, e.getMessage());
                System.out.println(USAGE);
                continue;
            }
            Channel ch = channel;
            if (ch == null || !ch.isActive()) {
                log.warn("MineNettyClient readConsole invoke, not connected, drop: {}", message);
                continue;
            }
            log.info("MineNettyClient readConsole invoke, {} >> {}", ch.id().asShortText(), JSON.toJSONString(message));
            send(ch, message);
        }
        log.info("MineNettyClient readConsole invoke, {} quit, shutting down", userId);
        group.shutdownGracefully();
    }

    /**
     * 把控制台输入的一行 JSON 解析并校验为 Message，校验失败抛 IllegalArgumentException：
     * 必须是 JSON 对象；type 必须是 CONSOLE_TYPES 之一（不区分大小写）；CHAT 必须有 to 和 content；
     * from 不用填，统一填为发送者 userId；即使填了别人也会被覆盖，不允许冒充。
     */
    static Message parseInput(String userId, String line) {
        JSONObject json;
        try {
            json = JSON.parseObject(line);
        } catch (JSONException | ClassCastException e) {
            throw new IllegalArgumentException("not a JSON object");
        }
        if (json == null) {
            throw new IllegalArgumentException("not a JSON object");
        }
        String typeName = json.getString("type");
        if (typeName == null || typeName.isBlank()) {
            throw new IllegalArgumentException("type is required, allowed: " + CONSOLE_TYPES);
        }
        MessageEnum type;
        try {
            type = MessageEnum.valueOf(typeName.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("unknown type " + typeName + ", allowed: " + CONSOLE_TYPES);
        }
        if (!CONSOLE_TYPES.contains(type)) {
            throw new IllegalArgumentException("type " + type + " can not be sent from console, allowed: " + CONSOLE_TYPES);
        }
        String to = json.getString("to");
        String content = json.getString("content");
        if (type == MessageEnum.CHAT) {
            if (to == null || to.isBlank()) {
                throw new IllegalArgumentException("CHAT requires to");
            }
            if (content == null || content.isBlank()) {
                throw new IllegalArgumentException("CHAT requires content");
            }
        }
        String from = json.getString("from");
        if (from != null && !from.equals(userId)) {
            log.warn("MineNettyClient parseInput invoke, from={} ignored, use sender {}", from, userId);
        }
        return new Message(type, userId, to, content);
    }

    private static void send(Channel ch, Message message) {
        ch.writeAndFlush(message).addListener((ChannelFuture f) -> {
            if (!f.isSuccess()) {
                log.warn("MineNettyClient send invoke, {} send failed: {}", ch.id().asShortText(), message, f.cause());
            }
        });
    }
}
