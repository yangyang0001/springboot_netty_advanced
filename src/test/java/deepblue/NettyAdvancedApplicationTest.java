package deepblue;

import com.alibaba.fastjson.JSON;
import deepblue.inaction_02_springboot.model.Message;
import deepblue.inaction_02_springboot.model.MessageType;
import deepblue.inaction_02_springboot.server.NettyServer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 启动完整的 Spring 容器（HTTP 随机端口 + Netty 随机端口），
 * 用最原始的 Socket 当客户端，验证 TCP 收发和 HTTP → TCP 推送。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "netty.server.port=0")
class NettyAdvancedApplicationTest {

    @Autowired
    private NettyServer nettyServer;

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    void chatHeartbeatAndHttpPush() throws IOException {
        try (TcpClient tom = new TcpClient(nettyServer.getPort());
             TcpClient jerry = new TcpClient(nettyServer.getPort())) {

            assertThat(tom.login("tom").getType()).isEqualTo(MessageType.LOGIN_OK);
            assertThat(jerry.login("jerry").getType()).isEqualTo(MessageType.LOGIN_OK);

            tom.send(new Message(MessageType.PING, "tom", null, null));
            assertThat(tom.receive().getType()).isEqualTo(MessageType.PONG);

            tom.send(new Message(MessageType.CHAT, "tom", "jerry", "hi jerry"));
            Message chat = jerry.receive();
            assertThat(chat.getType()).isEqualTo(MessageType.CHAT);
            assertThat(chat.getFrom()).isEqualTo("tom");
            assertThat(chat.getContent()).isEqualTo("hi jerry");

            tom.send(new Message(MessageType.CHAT, "tom", "nobody", "hello?"));
            assertThat(tom.receive().getType()).isEqualTo(MessageType.ERROR);

            String online = restTemplate.getForObject("/netty/online", String.class);
            assertThat(online).contains("tom", "jerry");

            String pushResult = restTemplate.postForObject("/netty/push?to=jerry&content=from-http", null, String.class);
            assertThat(pushResult).contains("\"sent\":true");
            Message push = jerry.receive();
            assertThat(push.getType()).isEqualTo(MessageType.PUSH);
            assertThat(push.getContent()).isEqualTo("from-http");
        }
    }

    @Test
    void duplicateLoginKicksOldConnection() throws IOException {
        try (TcpClient oldSpike = new TcpClient(nettyServer.getPort());
             TcpClient newSpike = new TcpClient(nettyServer.getPort())) {

            assertThat(oldSpike.login("spike").getType()).isEqualTo(MessageType.LOGIN_OK);
            assertThat(newSpike.login("spike").getType()).isEqualTo(MessageType.LOGIN_OK);

            // 旧连接先收到 KICKED，随后被服务端关闭（readLine 读到 EOF）。
            Message kicked = oldSpike.receive();
            assertThat(kicked.getType()).isEqualTo(MessageType.KICKED);
            assertThat(oldSpike.reader.readLine()).isNull();

            // 新连接仍然在线，可以收到推送。
            String pushResult = restTemplate.postForObject("/netty/push?to=spike&content=still-online", null, String.class);
            assertThat(pushResult).contains("\"sent\":true");
            assertThat(newSpike.receive().getContent()).isEqualTo("still-online");
        }
    }

    private static final class TcpClient implements AutoCloseable {

        private final Socket socket;
        private final BufferedReader reader;
        private final PrintWriter writer;

        TcpClient(int port) throws IOException {
            socket = new Socket("127.0.0.1", port);
            socket.setSoTimeout(5000);
            reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
            writer = new PrintWriter(socket.getOutputStream(), true, StandardCharsets.UTF_8);
        }

        Message login(String userId) throws IOException {
            send(new Message(MessageType.LOGIN, userId, null, null));
            return receive();
        }

        void send(Message message) {
            writer.print(JSON.toJSONString(message) + "\n");
            writer.flush();
        }

        Message receive() throws IOException {
            return JSON.parseObject(reader.readLine(), Message.class);
        }

        @Override
        public void close() throws IOException {
            socket.close();
        }
    }
}
