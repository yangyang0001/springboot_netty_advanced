package deepblue.inaction_02_springboot.model;

import com.alibaba.fastjson.JSON;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 应用层消息，线上传输格式为一行 JSON（以 \n 结尾），例如：
 * {"type":"CHAT","from":"tom","to":"jerry","content":"hi"}
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Message {

    private MessageEnum type;
    private String from;
    private String to;
    private String content;

    public static Message of(MessageEnum type, String content) {
        return new Message(type, "server", null, content);
    }

    public static void main(String[] args) {
        Message message = new Message();
        message.setType(MessageEnum.CHAT);
        message.setFrom("jerry");
        message.setTo("tom");
        message.setContent("Hello World");

        System.out.println(JSON.toJSONString(message));
    }
}
