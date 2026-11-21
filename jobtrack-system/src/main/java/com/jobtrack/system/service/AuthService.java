package com.jobtrack.system.service;

import com.jobtrack.system.pojo.AuthResponse;
import com.jobtrack.system.pojo.ChangePasswordRequest;
import com.jobtrack.system.pojo.LoginRequest;
import com.jobtrack.system.pojo.OnlineStatusResponse;
import com.jobtrack.system.pojo.ProfileRequest;
import com.jobtrack.system.pojo.RegisterRequest;
import com.jobtrack.system.pojo.UserView;
import com.jobtrack.system.security.AuthPrincipal;

/**
 * 登录、注册、账号资料与密码修改。
 */
public interface AuthService {

    AuthResponse login(LoginRequest request);

    AuthResponse register(RegisterRequest request);

    UserView me(AuthPrincipal principal);

    UserView updateProfile(AuthPrincipal principal, ProfileRequest request);

    void changePassword(AuthPrincipal principal, ChangePasswordRequest request);

    void logout(AuthPrincipal principal);

    OnlineStatusResponse online(AuthPrincipal principal);
}
