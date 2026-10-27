package deepblue.inaction_02_springboot.model;

/** 应用层协议的消息类型。 */
public enum MessageType {
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
    /** 服务端 → 客户端：由 HTTP 接口触发的主动推送。 */
    PUSH,
    /** 服务端 → 客户端：错误提示。 */
    ERROR,
    /** 服务端 → 客户端：同一个 userId 在别处登录，被踢下线，客户端收到后不再重连。 */
    KICKED
}
