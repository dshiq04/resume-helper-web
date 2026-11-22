package com.jobtrack.system.messaging;

import com.jobtrack.common.util.JsonUtils;
import com.jobtrack.system.service.BanService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * 消费封禁状态消息，更新 Redis 中的封禁人员集合。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class UserBanListener {

    private final BanService banService;

    @RabbitListener(queues = MessagingQueues.USER_BAN)
    public void handle(String payload) {
        try {
            UserBanMessage message = JsonUtils.fromJson(payload, UserBanMessage.class);
            if (message == null || message.getUserId() == null) {
                return;
            }
            banService.apply(message.getUserId(), message.isBanned(), message.getUntil());
        } catch (Exception exception) {
            log.warn("更新封禁集合失败: {}", exception.getMessage());
        }
    }
}
