package deepblue.inaction_03_app.client;

import deepblue.inaction_03_app.model.AppMessage;

/**
 * Netty 客户端事件回调，由界面层实现（桌面 App、测试等）。
 * ① 线程：所有回调都运行在 Netty 的 EventLoop 线程上，不是界面线程。
 * 界面层要自己切回界面线程（Swing 用 SwingUtilities.invokeLater，Android 用 runOnUiThread），
 * 并且回调里不能做阻塞操作，否则会卡住这条连接的读写。
 */
public interface ClientListener {

    /** TCP 连接建立成功，LOGIN 即将发出。 */
    default void onConnected() {
    }

    /** 收到服务端的一条消息（LOGIN_OK、ONLINE_LIST、USER_ONLINE、USER_OFFLINE、CHAT、ERROR、KICKED、PONG）。 */
    default void onMessage(AppMessage message) {
    }

    /** 连接断开，随后会自动重连（被踢下线时不会走这里，而是 onKicked）。 */
    default void onDisconnected() {
    }

    /** 即将在 delaySeconds 秒后重连。 */
    default void onReconnecting(int delaySeconds) {
    }

    /** 被踢下线，连接已关闭，不会再重连。 */
    default void onKicked() {
    }
}
