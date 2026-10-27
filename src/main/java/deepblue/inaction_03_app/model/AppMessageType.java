package deepblue.inaction_03_app.model;

/**
 * App 协议的消息类型：在 inaction_02 的基础上新增在线状态广播（ONLINE_LIST / USER_ONLINE / USER_OFFLINE）。
 * 独立定义，不修改 inaction_02 的 MessageEnum。
 */
public enum AppMessageType {
    /** 客户端 → 服务端：登录，绑定 userId 和 Channel。 */
    LOGIN,
    /** 服务端 → 客户端：登录成功。 */
    LOGIN_OK,
    /** 客户端 → 服务端：心跳。 */
    PING,
    /** 服务端 → 客户端：心跳响应。 */
    PONG,
    /** 客户端 → 服务端 → 目标客户端：点对点聊天。 */
    CHAT,
    /** 服务端 → 客户端：错误提示。 */
    ERROR,
    /** 服务端 → 客户端：同一个 userId 在别处登录，被踢下线，客户端收到后不再重连。 */
    KICKED,
    /** 服务端 → 客户端：登录成功后下发当前在线用户（不含自己），content 为 JSON 数组，例如 ["jerry","spike"]。 */
    ONLINE_LIST,
    /** 服务端 → 其他客户端：有人上线，content 为上线的 userId。 */
    USER_ONLINE,
    /** 服务端 → 其他客户端：有人下线，content 为下线的 userId。 */
    USER_OFFLINE;

    /** PING / PONG 是心跳，数量多且没有业务含义，日志打 debug。 */
    public boolean isHeartbeat() {
        return this == PING || this == PONG;
    }
}
