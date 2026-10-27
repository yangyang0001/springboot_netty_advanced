package deepblue.inaction_03_app.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * App 协议的消息，线上传输格式与 inaction_02 相同：一行 JSON（以 \n 结尾），例如：
 * {"content":"hi","from":"tom","to":"jerry","type":"CHAT"}
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AppMessage {

    private AppMessageType type;
    private String from;
    private String to;
    private String content;

    public static AppMessage of(AppMessageType type, String content) {
        return new AppMessage(type, "server", null, content);
    }
}
