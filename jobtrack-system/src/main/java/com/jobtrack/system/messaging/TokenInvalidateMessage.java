package com.jobtrack.system.messaging;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 令牌注销消息：退出登录 / 注销账号 / 忘记密码重置后，
 * 通过 RabbitMQ 通知消费端删除 Redis 中的令牌与权限信息。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TokenInvalidateMessage {

    /** 用户 ID；非空时注销该用户的所有会话。 */
    private Long userId;

    /** 会话 ID；非空时只注销这一个会话。 */
    private String sessionId;
}
