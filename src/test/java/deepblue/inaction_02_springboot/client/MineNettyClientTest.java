package deepblue.inaction_02_springboot.client;

import deepblue.inaction_02_springboot.model.Message;
import deepblue.inaction_02_springboot.model.MessageEnum;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 控制台 JSON 输入的解析和校验。 */
class MineNettyClientTest {

    @Test
    void parseChat() {
        Message message = MineNettyClient.parseInput("tom", "{\"type\":\"CHAT\",\"to\":\"jerry\",\"content\":\"hello\"}");
        assertThat(message).isEqualTo(new Message(MessageEnum.CHAT, "tom", "jerry", "hello"));
    }

    @Test
    void parsePingWithLowerCaseType() {
        Message message = MineNettyClient.parseInput("tom", "{\"type\":\"ping\"}");
        assertThat(message.getType()).isEqualTo(MessageEnum.PING);
    }

    @Test
    void parseChatWithoutFrom() {
        Message message = MineNettyClient.parseInput("jerry", "{\"content\":\"Hello World\",\"to\":\"tom\",\"type\":\"CHAT\"}");
        assertThat(message).isEqualTo(new Message(MessageEnum.CHAT, "jerry", "tom", "Hello World"));
    }

    @Test
    void fromIsAlwaysSender() {
        Message message = MineNettyClient.parseInput("tom", "{\"content\":\"hi\",\"from\":\"jerry\",\"to\":\"spike\",\"type\":\"CHAT\"}");
        assertThat(message.getFrom()).isEqualTo("tom");
    }

    @Test
    void rejectInvalidInput() {
        assertThatThrownBy(() -> MineNettyClient.parseInput("tom", "jerry hello"))
                .hasMessage("not a JSON object");
        assertThatThrownBy(() -> MineNettyClient.parseInput("tom", "[1,2]"))
                .hasMessage("not a JSON object");
        assertThatThrownBy(() -> MineNettyClient.parseInput("tom", "{\"to\":\"jerry\"}"))
                .hasMessageStartingWith("type is required");
        assertThatThrownBy(() -> MineNettyClient.parseInput("tom", "{\"type\":\"HELLO\"}"))
                .hasMessageStartingWith("unknown type HELLO");
        assertThatThrownBy(() -> MineNettyClient.parseInput("tom", "{\"type\":\"PUSH\",\"content\":\"x\"}"))
                .hasMessageStartingWith("type PUSH can not be sent from console");
        assertThatThrownBy(() -> MineNettyClient.parseInput("tom", "{\"type\":\"CHAT\",\"content\":\"hi\"}"))
                .hasMessage("CHAT requires to");
        assertThatThrownBy(() -> MineNettyClient.parseInput("tom", "{\"type\":\"CHAT\",\"to\":\"jerry\"}"))
                .hasMessage("CHAT requires content");
    }
}
