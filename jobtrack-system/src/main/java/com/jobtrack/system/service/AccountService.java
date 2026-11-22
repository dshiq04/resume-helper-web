package com.jobtrack.system.service;

import com.jobtrack.system.pojo.ContactUpdateRequest;
import com.jobtrack.system.pojo.UserProfileRequest;
import com.jobtrack.system.pojo.UserProfileView;
import com.jobtrack.system.pojo.UserView;
import com.jobtrack.system.security.AuthPrincipal;
import org.springframework.web.multipart.MultipartFile;

/**
 * 账户设置：扩展个人资料与联系方式。
 */
public interface AccountService {

    UserProfileView profile(AuthPrincipal principal);

    UserProfileView saveProfile(AuthPrincipal principal, UserProfileRequest request);

    /** 保存联系方式（手机号/邮箱），保存前检查是否已被其他账号占用。 */
    UserProfileView saveContact(AuthPrincipal principal, ContactUpdateRequest request);

    UserProfileView uploadAvatar(AuthPrincipal principal, MultipartFile file);
}
