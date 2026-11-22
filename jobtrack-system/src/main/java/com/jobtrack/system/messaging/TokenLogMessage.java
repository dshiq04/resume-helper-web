package com.jobtrack.system.messaging;

/** 授权码使用日志，通过 RabbitMQ 异步落库。 */
public record TokenLogMessage(
        Long tenantId,
        Long userId,
        Long tokenId,
        String tokenName,
        String codePrefix,
        String scope,
        String resources,
        String result,
        String ip,
        String userAgent) {
}
