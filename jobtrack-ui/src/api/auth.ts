import { request } from "./client";
import type { AuthResponse, CaptchaResponse, OnlineStatus, User } from "./types";

/** 对应后端 AuthController。 */
export const authApi = {
  status() {
    return request<{ enabled: boolean; authenticated: boolean }>("/auth/status", {
      handleUnauthorized: false,
    });
  },
  captcha() {
    return request<CaptchaResponse>("/auth/captcha", { handleUnauthorized: false });
  },
  login(input: { username: string; password: string; captchaId?: string; captchaCode?: string }) {
    return request<AuthResponse>("/auth/login", {
      method: "POST",
      data: input,
      handleUnauthorized: false,
    });
  },
  register(input: {
    username: string;
    password: string;
    confirmPassword?: string;
    displayName?: string;
    tenantName?: string;
    contactType?: "phone" | "email";
    countryCode?: string;
    phone?: string;
    email?: string;
    agreementAccepted?: boolean;
    captchaId?: string;
    captchaCode?: string;
  }) {
    return request<AuthResponse>("/auth/register", {
      method: "POST",
      data: input,
      handleUnauthorized: false,
    });
  },
  me() {
    return request<User>("/auth/me");
  },
  updateProfile(displayName: string) {
    return request<User>("/auth/me", { method: "PUT", data: { displayName } });
  },
  changePassword(
    currentPassword: string,
    newPassword: string,
    contact: string,
    contactType?: "phone" | "email",
  ) {
    return request<{ changed: boolean }>("/auth/change-password", {
      method: "POST",
      data: { currentPassword, newPassword, contact, contactType },
      // 密码校验失败不能触发全局 401 退出登录。
      handleUnauthorized: false,
    });
  },
  logout() {
    return request<{ loggedOut: boolean }>("/auth/logout", { method: "POST" });
  },
  online() {
    return request<OnlineStatus>("/auth/online");
  },
  /** 忘记密码第一步：验证用户名与手机号/邮箱。 */
  forgotPasswordVerify(input: { username: string; contactType?: string; contact: string }) {
    return request<{ verified: boolean }>("/auth/forgot-password/verify", {
      method: "POST",
      data: input,
      handleUnauthorized: false,
    });
  },
  /** 忘记密码第二步：重置密码并注销全部旧令牌。 */
  forgotPasswordReset(input: {
    username: string;
    contactType?: string;
    contact: string;
    newPassword: string;
    confirmPassword: string;
  }) {
    return request<{ reset: boolean }>("/auth/forgot-password/reset", {
      method: "POST",
      data: input,
      handleUnauthorized: false,
    });
  },
};
