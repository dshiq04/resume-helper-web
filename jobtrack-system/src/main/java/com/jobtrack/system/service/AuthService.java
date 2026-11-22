package com.jobtrack.system.service;

import com.jobtrack.system.pojo.vo.AuthVO;
import com.jobtrack.system.pojo.dto.ChangePasswordDTO;
import com.jobtrack.system.pojo.dto.ForgotPasswordResetDTO;
import com.jobtrack.system.pojo.dto.ForgotPasswordVerifyDTO;
import com.jobtrack.system.pojo.dto.LoginDTO;
import com.jobtrack.system.pojo.vo.OnlineStatusVO;
import com.jobtrack.system.pojo.dto.ProfileDTO;
import com.jobtrack.system.pojo.dto.RegisterDTO;
import com.jobtrack.system.pojo.vo.UserVO;
import com.jobtrack.system.security.AuthPrincipal;

/**
 * 登录、注册、账号资料与密码修改。
 */
public interface AuthService {

    AuthVO login(LoginDTO request);

    AuthVO register(RegisterDTO request);

    UserVO me(AuthPrincipal principal);

    UserVO updateProfile(AuthPrincipal principal, ProfileDTO request);

    void changePassword(AuthPrincipal principal, ChangePasswordDTO request);

    /** 忘记密码第一步：验证用户名与保留的手机号/邮箱。 */
    void forgotPasswordVerify(ForgotPasswordVerifyDTO request);

    /** 忘记密码第二步：重置密码，成功后注销该用户全部令牌（RabbitMQ）。 */
    void forgotPasswordReset(ForgotPasswordResetDTO request);

    void logout(AuthPrincipal principal);

    OnlineStatusVO online(AuthPrincipal principal);
}
