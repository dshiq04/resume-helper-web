package com.jobtrack.system.messaging;

import com.jobtrack.common.util.JsonUtils;
import com.jobtrack.system.service.AccessTokenLogWriter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * 消费授权码使用日志并落库。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TokenLogListener {

    private final AccessTokenLogWriter logWriter;

    @RabbitListener(queues = MessagingQueues.TOKEN_LOG)
    public void handle(String payload) {
        try {
            TokenLogMessage message = JsonUtils.fromJson(payload, TokenLogMessage.class);
            if (message != null) {
                logWriter.save(message);
            }
        } catch (Exception exception) {
            log.warn("写入授权码日志失败: {}", exception.getMessage());
        }
    }
}
