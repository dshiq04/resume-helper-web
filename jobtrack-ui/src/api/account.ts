import { request } from "./client";
import { encryptedRequest } from "../utils/secureForm";
import type {
  AccessToken,
  AccessTokenInput,
  AccessTokenLogPage,
  User,
  UserProfile,
  CancelStatus,
} from "./types";

/** 对应后端 AccountController。 */
export const accountApi = {
  profile() {
    return request<UserProfile>("/account/profile");
  },
  saveProfile(input: UserProfile) {
    return request<UserProfile>("/account/profile", { method: "PUT", data: input });
  },
  /** 保存联系方式：手机号/邮箱占用检查通过后才能修改。 */
  saveContact(input: { countryCode?: string; phone?: string; email?: string }) {
    return request<UserProfile>("/account/contact", { method: "PUT", data: input });
  },
  uploadAvatar(file: File) {
    const data = new FormData();
    data.append("file", file);
    return request<UserProfile>("/account/avatar", { method: "POST", data });
  },
  tokens() {
    return request<AccessToken[]>("/account/tokens");
  },
  createToken(input: AccessTokenInput) {
    return request<AccessToken>("/account/tokens", { method: "POST", data: input });
  },
  updateTokenStatus(id: number, enabled: boolean) {
    return request<AccessToken>(`/account/tokens/${id}/enabled`, {
      method: "PUT",
      data: { enabled },
    });
  },
  deleteToken(id: number) {
    return request<{ deleted: boolean }>(`/account/tokens/${id}`, { method: "DELETE" });
  },
  tokenLogs(params: { page?: number; pageSize?: number; name?: string; result?: string } = {}) {
    const search = new URLSearchParams();
    search.set("page", String(params.page ?? 1));
    search.set("pageSize", String(params.pageSize ?? 10));
    if (params.name) search.set("name", params.name);
    if (params.result) search.set("result", params.result);
    return request<AccessTokenLogPage>(`/account/tokens/logs?${search.toString()}`);
  },
  /** 用户注销条件检查：投递已清除、简历库已删除、没有普通用户以外的权限。 */
  cancelStatus() {
    return request<CancelStatus>("/account/cancel/status");
  },
  /** 验证保留的手机号或邮箱与密码。 */
  cancelVerify(input: { contactType?: string; contact: string; password: string }) {
    return encryptedRequest<{ verified: boolean }>("/account/cancel/verify", input, {
      handleUnauthorized: false,
    });
  },
  /** 最终确认注销（需要验证码）。 */
  cancelConfirm(input: {
    captchaId?: string;
    captchaCode: string;
    contactType?: string;
    contact: string;
    password: string;
  }) {
    return encryptedRequest<{ cancelled: boolean }>("/account/cancel/confirm", input, {
      handleUnauthorized: false,
    });
  },
};
