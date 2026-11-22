package com.jobtrack.system.messaging;

import com.jobtrack.common.util.JsonUtils;
import com.jobtrack.system.service.UserOperationLogWriter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * 消费用户操作记录并落库。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class UserOperationListener {

    private final UserOperationLogWriter logWriter;

    @RabbitListener(queues = MessagingQueues.USER_OPERATION)
    public void handle(String payload) {
        try {
            UserOperationMessage message = JsonUtils.fromJson(payload, UserOperationMessage.class);
            logWriter.save(message);
        } catch (Exception exception) {
            log.warn("写入用户操作记录失败: {}", exception.getMessage());
        }
    }
}
