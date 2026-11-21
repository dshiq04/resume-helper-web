import { request } from "./client";
import type {
  AccessToken,
  AccessTokenInput,
  AccessTokenLog,
  User,
  UserProfile,
} from "./types";

/** 对应后端 AccountController。 */
export const accountApi = {
  profile() {
    return request<UserProfile>("/account/profile");
  },
  saveProfile(input: UserProfile) {
    return request<UserProfile>("/account/profile", { method: "PUT", data: input });
  },
  changeUsername(username: string) {
    return request<User>("/account/username", { method: "PUT", data: { username } });
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
  tokenLogs(params: { limit?: number; tokenId?: number; result?: string } = {}) {
    const search = new URLSearchParams();
    search.set("limit", String(params.limit ?? 50));
    if (params.tokenId) search.set("tokenId", String(params.tokenId));
    if (params.result) search.set("result", params.result);
    return request<AccessTokenLog[]>(`/account/tokens/logs?${search.toString()}`);
  },
};
