package deepblue.inaction_03_app.ui;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

/** 聊天窗口中的一行：自己发的、别人发的、系统提示、错误提示。 */
record ChatItem(Kind kind, String header, String text) {

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm:ss");

    enum Kind {
        MINE, OTHER, SYSTEM, ERROR
    }

    static ChatItem mine(String to, String text) {
        return new ChatItem(Kind.MINE, "我 → " + to + "  " + now(), text);
    }

    static ChatItem other(String from, String text) {
        return new ChatItem(Kind.OTHER, from + " → 我  " + now(), text);
    }

    static ChatItem system(String text) {
        return new ChatItem(Kind.SYSTEM, null, text + "  " + now());
    }

    static ChatItem error(String text) {
        return new ChatItem(Kind.ERROR, null, text + "  " + now());
    }

    private static String now() {
        return LocalTime.now().format(TIME);
    }
}
