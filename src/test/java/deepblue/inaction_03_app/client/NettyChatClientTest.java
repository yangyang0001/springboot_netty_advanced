package deepblue.inaction_03_app.client;

import com.alibaba.fastjson.JSON;
import deepblue.inaction_03_app.model.AppMessage;
import deepblue.inaction_03_app.model.AppMessageType;
import deepblue.inaction_03_app.server.AppNettyServer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

/**
 * App 客户端（NettyChatClient）连接 inaction_03 的 AppNettyServer：
 * 在线实时广播、双向聊天、回调线程、被踢下线、主动关闭后不再重连。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "netty.server.port=0")
class NettyChatClientTest {

    @Autowired
    private AppNettyServer appNettyServer;

    @Test
    void onlineBroadcastAndChatBothWays() throws Exception {
        RecordingListener tomEvents = new RecordingListener();
        RecordingListener jerryEvents = new RecordingListener();
        NettyChatClient tom = client("app-tom", tomEvents);
        NettyChatClient jerry = client("app-jerry", jerryEvents);
        try {
            tom.start();
            assertThat(tomEvents.await(AppMessageType.LOGIN_OK).getContent()).isEqualTo("welcome app-tom");
            tomEvents.await(AppMessageType.ONLINE_LIST);
            // 回调运行在 Netty 的 EventLoop 线程上，不是调用方线程，所以界面层必须自己切回界面线程。
            assertThat(tomEvents.lastThread).startsWith("nioEventLoopGroup");

            // jerry 上线：jerry 收到的在线列表里有 tom，tom 收到 jerry 的上线广播。
            jerry.start();
            assertThat(jerryEvents.await(AppMessageType.LOGIN_OK).getContent()).isEqualTo("welcome app-jerry");
            assertThat(JSON.parseArray(jerryEvents.await(AppMessageType.ONLINE_LIST).getContent(), String.class))
                    .contains("app-tom").doesNotContain("app-jerry");
            assertThat(tomEvents.await(AppMessageType.USER_ONLINE).getContent()).isEqualTo("app-jerry");

            assertThat(tom.send(new AppMessage(AppMessageType.CHAT, "app-tom", "app-jerry", "hi jerry"))
                    .await(5, TimeUnit.SECONDS)).isTrue();
            AppMessage toJerry = jerryEvents.await(AppMessageType.CHAT);
            assertThat(toJerry.getFrom()).isEqualTo("app-tom");
            assertThat(toJerry.getContent()).isEqualTo("hi jerry");

            jerry.send(new AppMessage(AppMessageType.CHAT, "app-jerry", "app-tom", "hi tom"));
            AppMessage toTom = tomEvents.await(AppMessageType.CHAT);
            assertThat(toTom.getFrom()).isEqualTo("app-jerry");
            assertThat(toTom.getContent()).isEqualTo("hi tom");

            // jerry 下线：tom 收到下线广播。
            jerry.close();
            assertThat(tomEvents.await(AppMessageType.USER_OFFLINE).getContent()).isEqualTo("app-jerry");
        } finally {
            tom.close();
            jerry.close();
        }
        // 主动关闭后不再重连。
        TimeUnit.MILLISECONDS.sleep(1500);
        assertThat(tom.isConnected()).isFalse();
        assertThat(tomEvents.reconnecting).isEmpty();
    }

    @Test
    void duplicateLoginKicksOldClientWithoutReconnect() throws Exception {
        RecordingListener watcherEvents = new RecordingListener();
        RecordingListener oldEvents = new RecordingListener();
        RecordingListener newEvents = new RecordingListener();
        NettyChatClient watcher = client("app-watcher", watcherEvents);
        NettyChatClient oldSpike = client("app-spike", oldEvents);
        NettyChatClient newSpike = client("app-spike", newEvents);
        try {
            watcher.start();
            watcherEvents.await(AppMessageType.LOGIN_OK);
            oldSpike.start();
            oldEvents.await(AppMessageType.LOGIN_OK);
            assertThat(watcherEvents.await(AppMessageType.USER_ONLINE).getContent()).isEqualTo("app-spike");

            newSpike.start();
            newEvents.await(AppMessageType.LOGIN_OK);
            assertThat(oldEvents.await(AppMessageType.KICKED).getContent()).isEqualTo("app-spike logged in elsewhere");
            assertThat(oldEvents.kicked.poll(5, TimeUnit.SECONDS)).isTrue();

            // 被踢的旧客户端不重连；app-spike 一直在线，所以 watcher 收不到它的下线广播。
            TimeUnit.MILLISECONDS.sleep(1500);
            assertThat(oldEvents.reconnecting).isEmpty();
            assertThat(oldSpike.isConnected()).isFalse();
            assertThat(watcherEvents.messages).noneMatch(m -> m.getType() == AppMessageType.USER_OFFLINE);
        } finally {
            watcher.close();
            oldSpike.close();
            newSpike.close();
        }
    }

    private NettyChatClient client(String userId, ClientListener listener) {
        return new NettyChatClient(userId, "127.0.0.1", appNettyServer.getPort(), listener);
    }

    /** 把回调事件记录下来，测试线程从队列里取。 */
    private static final class RecordingListener implements ClientListener {

        private final BlockingQueue<AppMessage> messages = new LinkedBlockingQueue<>();
        private final BlockingQueue<Integer> reconnecting = new LinkedBlockingQueue<>();
        private final BlockingQueue<Boolean> kicked = new LinkedBlockingQueue<>();
        private volatile String lastThread;

        @Override
        public void onMessage(AppMessage message) {
            lastThread = Thread.currentThread().getName();
            messages.add(message);
        }

        @Override
        public void onReconnecting(int delaySeconds) {
            reconnecting.add(delaySeconds);
        }

        @Override
        public void onKicked() {
            kicked.add(true);
        }

        /** 等待指定类型的消息，期间收到的其他消息直接丢弃。 */
        AppMessage await(AppMessageType type) throws InterruptedException {
            long deadline = System.currentTimeMillis() + 5000;
            while (System.currentTimeMillis() < deadline) {
                AppMessage message = messages.poll(100, TimeUnit.MILLISECONDS);
                if (message != null && message.getType() == type) {
                    return message;
                }
            }
            return fail("no " + type + " within 5s");
        }
    }
}
