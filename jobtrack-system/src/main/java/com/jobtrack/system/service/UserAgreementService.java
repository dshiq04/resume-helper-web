package com.jobtrack.system.service;

import com.jobtrack.system.pojo.dto.AgreementAcceptDTO;
import com.jobtrack.system.pojo.vo.UserAgreementVO;
import com.jobtrack.system.security.AuthPrincipal;

/**
 * 用户协议签署记录：注册时写入、登录后补签、管理员查看与导出。
 */
public interface UserAgreementService {

    /** 注册成功后记录签署信息。 */
    void recordRegistration(Long tenantId, Long userId, String signerName,
                            String language, String timezone);

    /** 登录后补签：写入签署语言、时间、地点与账号信息。 */
    UserAgreementVO accept(AuthPrincipal principal, AgreementAcceptDTO request, String remoteIp);

    /** 查询任意用户的签署记录，未签署时返回 signed=false。 */
    UserAgreementVO find(Long userId);
}
