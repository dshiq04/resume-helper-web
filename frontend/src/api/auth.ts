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
    displayName?: string;
    tenantName?: string;
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
  changePassword(currentPassword: string, newPassword: string, contact: string) {
    return request<{ changed: boolean }>("/auth/change-password", {
      method: "POST",
      data: { currentPassword, newPassword, contact },
    });
  },
  logout() {
    return request<{ loggedOut: boolean }>("/auth/logout", { method: "POST" });
  },
  online() {
    return request<OnlineStatus>("/auth/online");
  },
};
