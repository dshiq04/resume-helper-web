package com.jobtrack.system.messaging;

import com.jobtrack.common.util.JsonUtils;
import com.jobtrack.system.config.AppProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;

/**
 * RabbitMQ 发布器。Broker 不可用时自动降级为同步处理，
 * 由调用方执行本地删除 / 直接写库，避免阻塞业务。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AsyncTaskPublisher {

    private final RabbitTemplate rabbitTemplate;
    private final AppProperties appProperties;

    private volatile boolean available = true;

    public boolean publishFileCleanup(String key) {
        if (key == null || key.isBlank() || !enabled() || !available) {
            return false;
        }
        try {
            rabbitTemplate.convertAndSend("", MessagingQueues.FILE_CLEANUP,
                    JsonUtils.toJson(new FileCleanupMessage(key)));
            return true;
        } catch (Exception exception) {
            degrade(exception);
            return false;
        }
    }

    public boolean publishTokenLog(TokenLogMessage message) {
        if (!enabled() || !available) {
            return false;
        }
        try {
            rabbitTemplate.convertAndSend("", MessagingQueues.TOKEN_LOG,
                    JsonUtils.toJson(message));
            return true;
        } catch (Exception exception) {
            degrade(exception);
            return false;
        }
    }

    /**
     * 发布令牌注销消息。返回 false 表示发送失败，调用方应同步删除。
     */
    public boolean publishTokenInvalidate(Long userId, String sessionId) {
        if (!enabled() || !available) {
            return false;
        }
        try {
            rabbitTemplate.convertAndSend("", MessagingQueues.TOKEN_INVALIDATE,
                    JsonUtils.toJson(new TokenInvalidateMessage(userId, sessionId)));
            return true;
        } catch (Exception exception) {
            degrade(exception);
            return false;
        }
    }

    /**
     * 发布封禁状态变更消息。返回 false 表示发送失败，调用方应同步更新封禁集合。
     */
    public boolean publishUserBan(UserBanMessage message) {
        if (message == null || message.getUserId() == null || !enabled() || !available) {
            return false;
        }
        try {
            rabbitTemplate.convertAndSend("", MessagingQueues.USER_BAN,
                    JsonUtils.toJson(message));
            return true;
        } catch (Exception exception) {
            degrade(exception);
            return false;
        }
    }

    private boolean enabled() {
        return appProperties.getRabbit().isEnabled();
    }

    private void degrade(Exception exception) {
        if (available) {
            available = false;
            log.warn("RabbitMQ 不可用，后续任务降级为同步执行: {}", exception.getMessage());
        }
    }
}
