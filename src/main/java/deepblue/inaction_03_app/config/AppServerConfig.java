package deepblue.inaction_03_app.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/** App 服务端配置，对应 netty.app-server.*；不配置时使用这里的默认值，和 inaction_02 的 netty.server.* 互不影响。 */
@Data
@Component
@ConfigurationProperties(prefix = "netty.app-server")
public class AppServerConfig {

    /** TCP 监听端口；0 表示随机端口（测试用）。inaction_02 用 9000，这里用 9100。 */
    private int port = 9100;

    /** ① boss 线程数，只负责 accept。 */
    private int bossThreads = 1;

    /** ① worker 线程数，0 表示使用 Netty 默认值（CPU 核数 * 2）。 */
    private int workerThreads = 0;

    /** ① 业务线程数。 */
    private int bizThreads = 8;

    /** ⑥ 读空闲秒数，超过这个时间没收到任何数据（包括心跳）就断开。 */
    private int readerIdleSeconds = 60;

    /** ④ 单帧最大字节数。 */
    private int maxFrameLength = 8192;

    /** ⑦ 写缓冲区低水位 / 高水位（字节）。 */
    private int writeBufferLowWaterMark = 32 * 1024;
    private int writeBufferHighWaterMark = 64 * 1024;
}
