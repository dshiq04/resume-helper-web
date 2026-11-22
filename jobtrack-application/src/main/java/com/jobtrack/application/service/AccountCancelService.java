package com.jobtrack.application.service;

import com.jobtrack.application.pojo.CancelConfirmRequest;
import com.jobtrack.application.pojo.CancelStatusResponse;
import com.jobtrack.application.pojo.CancelVerifyRequest;
import com.jobtrack.system.security.AuthPrincipal;

/**
 * 用户注销：条件检查、身份验证与注销执行。
 */
public interface AccountCancelService {

    /** 注销条件：投递已清除、简历库已删除、没有普通用户以外的权限。 */
    CancelStatusResponse status(AuthPrincipal principal);

    /** 验证保留的手机号或邮箱与密码。 */
    void verify(AuthPrincipal principal, CancelVerifyRequest request);

    /** 执行注销：改状态为注销、删除 Redis 令牌与权限（RabbitMQ）。 */
    void confirm(AuthPrincipal principal, CancelConfirmRequest request);
}
