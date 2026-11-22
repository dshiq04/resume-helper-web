package com.jobtrack.system.messaging;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 用户封禁状态变更消息：由 RabbitMQ 通知各实例更新 Redis 中的人员封禁集合。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserBanMessage {

    private Long userId;

    private boolean banned;

    /** 暂时封禁的到期时间；永久封禁为空。 */
    private LocalDateTime until;
}
