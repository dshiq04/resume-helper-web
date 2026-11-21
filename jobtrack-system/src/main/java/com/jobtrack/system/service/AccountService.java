package com.jobtrack.system.service;

import com.jobtrack.system.pojo.UserProfileRequest;
import com.jobtrack.system.pojo.UserProfileView;
import com.jobtrack.system.pojo.UserView;
import com.jobtrack.system.security.AuthPrincipal;
import org.springframework.web.multipart.MultipartFile;

/**
 * 账户设置：扩展个人资料与用户名修改。
 */
public interface AccountService {

    UserProfileView profile(AuthPrincipal principal);

    UserProfileView saveProfile(AuthPrincipal principal, UserProfileRequest request);

    UserProfileView uploadAvatar(AuthPrincipal principal, MultipartFile file);

    UserView changeUsername(AuthPrincipal principal, String rawUsername);
}
