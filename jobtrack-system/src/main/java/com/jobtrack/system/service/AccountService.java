package com.jobtrack.system.service;

import com.jobtrack.system.pojo.dto.ContactUpdateDTO;
import com.jobtrack.system.pojo.dto.UserProfileDTO;
import com.jobtrack.system.pojo.vo.UserProfileVO;
import com.jobtrack.system.pojo.vo.UserVO;
import com.jobtrack.system.security.AuthPrincipal;
import org.springframework.web.multipart.MultipartFile;

/**
 * 账户设置：扩展个人资料与联系方式。
 */
public interface AccountService {

    UserProfileVO profile(AuthPrincipal principal);

    UserProfileVO saveProfile(AuthPrincipal principal, UserProfileDTO request);

    /** 保存联系方式（手机号/邮箱），保存前检查是否已被其他账号占用。 */
    UserProfileVO saveContact(AuthPrincipal principal, ContactUpdateDTO request);

    UserProfileVO uploadAvatar(AuthPrincipal principal, MultipartFile file);
}
