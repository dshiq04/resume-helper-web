import { request } from "./client";
import { encryptedRequest } from "../utils/secureForm";
import type {
  AuthResponse,
  CaptchaResponse,
  OnlineStatus,
  User,
  UserAgreementRecord,
} from "./types";

/** 对应后端 AuthController。登录、注册、改密、找回密码表单统一加密后提交。 */
export const authApi = {
  status() {
    return request<{ enabled: boolean; authenticated: boolean }>("/auth/status", {
      handleUnauthorized: false,
    });
  },
  captcha() {
    return request<CaptchaResponse>("/auth/captcha", { handleUnauthorized: false });
  },
  /** 获取表单加密公钥；前端按 chunkSize 分块做 RSA 加密。 */
  publicKey() {
    return request<{ algorithm: string; publicKey: string; chunkSize: number }>(
      "/auth/public-key",
      { handleUnauthorized: false },
    );
  },
  async login(input: { username: string; password: string; captchaId?: string; captchaCode?: string }) {
    return encryptedRequest<AuthResponse>("/auth/login", input, {
      handleUnauthorized: false,
    });
  },
  async register(input: {
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
    agreementLanguage?: string;
    agreementTimezone?: string;
    captchaId?: string;
    captchaCode?: string;
  }) {
    return encryptedRequest<AuthResponse>("/auth/register", input, {
      handleUnauthorized: false,
    });
  },
  me() {
    return request<User>("/auth/me");
  },
  /** 当前账号的用户协议签署状态，用于登录后判断是否弹出补签。 */
  agreement() {
    return request<UserAgreementRecord>("/auth/agreement");
  },
  /** 登录后补签用户协议，同时上传签署语言、时区、地点等信息。 */
  acceptAgreement(input: {
    language?: string;
    location?: string;
    timezone?: string;
    locale?: string;
  }) {
    return request<UserAgreementRecord>("/auth/agreement", {
      method: "POST",
      data: input,
    });
  },
  updateProfile(displayName: string) {
    return request<User>("/auth/me", { method: "PUT", data: { displayName } });
  },
  async changePassword(
    currentPassword: string,
    newPassword: string,
    confirmPassword: string,
    contact: string,
    contactType?: "phone" | "email",
  ) {
    return encryptedRequest<{ changed: boolean }>("/auth/change-password", {
      currentPassword,
      newPassword,
      confirmPassword,
      contact,
      contactType,
    }, {
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
  async forgotPasswordVerify(input: { username: string; contactType?: string; contact: string }) {
    return encryptedRequest<{ verified: boolean }>("/auth/forgot-password/verify", input, {
      handleUnauthorized: false,
    });
  },
  /** 忘记密码第二步：重置密码并注销全部旧令牌。 */
  async forgotPasswordReset(input: {
    username: string;
    contactType?: string;
    contact: string;
    newPassword: string;
    confirmPassword: string;
  }) {
    return encryptedRequest<{ reset: boolean }>("/auth/forgot-password/reset", input, {
      handleUnauthorized: false,
    });
  },
};
