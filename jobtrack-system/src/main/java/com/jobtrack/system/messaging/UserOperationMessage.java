package com.jobtrack.system.messaging;

import java.time.LocalDateTime;

/** 用户操作记录消息，由 AOP 发布、RabbitMQ 消费者异步落库。 */
public record UserOperationMessage(
        Long tenantId,
        Long userId,
        String username,
        String displayName,
        String operationType,
        String action,
        String description,
        String method,
        String path,
        String ip,
        String userAgent,
        LocalDateTime createdAt) {
}
