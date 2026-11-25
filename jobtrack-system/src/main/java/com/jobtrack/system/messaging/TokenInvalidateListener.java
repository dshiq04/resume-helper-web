package com.jobtrack.system.messaging;

import com.jobtrack.common.util.JsonUtils;
import com.jobtrack.system.security.SessionService;
import com.rabbitmq.client.Channel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * 消费令牌注销消息：从 Redis 中删除令牌与权限信息。
 *
 * <p>使用手动确认：只有删除成功后才 ack；处理失败时 nack 并重新入队，
 * 由 RabbitMQ 持续重试，避免异步消息被吞掉后旧令牌仍然有效。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TokenInvalidateListener {

    private final SessionService sessionService;

    @RabbitListener(queues = MessagingQueues.TOKEN_INVALIDATE,
            containerFactory = "manualAckListenerContainerFactory")
    public void handle(String payload, Channel channel,
                       @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag) throws IOException {
        try {
            TokenInvalidateMessage message = JsonUtils.fromJson(payload,
                    TokenInvalidateMessage.class);
            if (message != null) {
                if (message.getUserId() != null) {
                    sessionService.deleteByUser(message.getUserId(), message.getTenantId());
                } else if (message.getSessionId() != null && !message.getSessionId().isBlank()) {
                    sessionService.delete(message.getSessionId());
                }
            }
            channel.basicAck(deliveryTag, false);
        } catch (Exception exception) {
            log.warn("注销令牌失败，消息将重新入队重试: {}", exception.getMessage());
            channel.basicNack(deliveryTag, false, true);
        }
    }
}
