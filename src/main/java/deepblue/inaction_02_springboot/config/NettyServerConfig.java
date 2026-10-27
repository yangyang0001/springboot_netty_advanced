package deepblue.inaction_02_springboot.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/** Netty 服务端配置，对应 application.properties 中的 netty.server.*。 */
@Data
@Component
@ConfigurationProperties(prefix = "netty.server")
public class NettyServerConfig {

    /** TCP 监听端口；0 表示随机端口（测试用）。 */
    private int port = 9000;

    /** ① boss 线程数，只负责 accept。 */
    private int bossThreads = 1;

    /** ① worker 线程数，0 表示使用 Netty 默认值（CPU 核数 * 2）。 */
    private int workerThreads = 0;

    /** ① 业务线程数，可能阻塞的业务 Handler 跑在这里，不占用 IO 线程。 */
    private int bizThreads = 16;

    /** ⑥ 读空闲秒数，超过这个时间没收到任何数据（包括心跳）就断开。 */
    private int readerIdleSeconds = 60;

    /** ④ 单帧最大字节数，防止恶意超长数据撑爆内存。 */
    private int maxFrameLength = 8192;

    /** ⑦ 写缓冲区低水位 / 高水位（字节）。 */
    private int writeBufferLowWaterMark = 32 * 1024;
    private int writeBufferHighWaterMark = 64 * 1024;
}
