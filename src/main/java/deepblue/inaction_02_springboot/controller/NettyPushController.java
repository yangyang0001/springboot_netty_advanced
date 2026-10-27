package deepblue.inaction_02_springboot.controller;

import deepblue.inaction_02_springboot.model.Message;
import deepblue.inaction_02_springboot.model.MessageType;
import deepblue.inaction_02_springboot.session.ChannelSessionManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.Set;

/**
 * HTTP → TCP：其它系统通过 HTTP 调用，服务端经由 Netty 长连接主动推送给客户端。
 * 这是 Spring Boot + Netty 最典型的组合方式。
 */
@Slf4j
@RestController
@RequestMapping("/netty")
@RequiredArgsConstructor
public class NettyPushController {

    private final ChannelSessionManager sessionManager;

    @GetMapping("/online")
    public Set<String> online() {
        return sessionManager.onlineUsers();
    }

    @PostMapping("/push")
    public Map<String, Object> push(@RequestParam String to, @RequestParam String content) {
        log.info("NettyPushController push invoke, http push to={} content={}", to, content);
        boolean sent = sessionManager.send(to, Message.of(MessageType.PUSH, content));
        log.info("NettyPushController push invoke, http push to={} sent={}", to, sent);
        return Map.of("to", to, "sent", sent);
    }

    @PostMapping("/broadcast")
    public Map<String, Object> broadcast(@RequestParam String content) {
        log.info("NettyPushController broadcast invoke, http broadcast content={}", content);
        int count = sessionManager.broadcast(Message.of(MessageType.PUSH, content));
        return Map.of("sent", count);
    }
}
