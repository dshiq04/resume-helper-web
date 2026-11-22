package com.jobtrack.application.service;

import com.jobtrack.application.pojo.dto.CancelConfirmDTO;
import com.jobtrack.application.pojo.vo.CancelStatusVO;
import com.jobtrack.application.pojo.dto.CancelVerifyDTO;
import com.jobtrack.system.security.AuthPrincipal;

/**
 * 用户注销：条件检查、身份验证与注销执行。
 */
public interface AccountCancelService {

    /** 注销条件：投递已清除、简历库已删除、没有普通用户以外的权限。 */
    CancelStatusVO status(AuthPrincipal principal);

    /** 验证保留的手机号或邮箱与密码。 */
    void verify(AuthPrincipal principal, CancelVerifyDTO request);

    /** 执行注销：改状态为注销、删除 Redis 令牌与权限（RabbitMQ）。 */
    void confirm(AuthPrincipal principal, CancelConfirmDTO request);
}
