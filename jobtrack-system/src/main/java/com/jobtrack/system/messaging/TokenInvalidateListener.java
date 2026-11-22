package com.jobtrack.system.messaging;

import com.jobtrack.common.util.JsonUtils;
import com.jobtrack.system.security.SessionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * 消费令牌注销消息：从 Redis 中删除令牌与权限信息。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TokenInvalidateListener {

    private final SessionService sessionService;

    @RabbitListener(queues = MessagingQueues.TOKEN_INVALIDATE)
    public void handle(String payload) {
        try {
            TokenInvalidateMessage message = JsonUtils.fromJson(payload, TokenInvalidateMessage.class);
            if (message == null) {
                return;
            }
            if (message.getUserId() != null) {
                sessionService.deleteByUser(message.getUserId(), null);
            } else if (message.getSessionId() != null && !message.getSessionId().isBlank()) {
                sessionService.delete(message.getSessionId());
            }
        } catch (Exception exception) {
            log.warn("注销令牌失败: {}", exception.getMessage());
        }
    }
}
