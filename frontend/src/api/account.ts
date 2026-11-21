import { request } from "./client";
import type {
  AccessToken,
  AccessTokenInput,
  AccessTokenLogPage,
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
  tokenLogs(params: { page?: number; pageSize?: number; name?: string; result?: string } = {}) {
    const search = new URLSearchParams();
    search.set("page", String(params.page ?? 1));
    search.set("pageSize", String(params.pageSize ?? 10));
    if (params.name) search.set("name", params.name);
    if (params.result) search.set("result", params.result);
    return request<AccessTokenLogPage>(`/account/tokens/logs?${search.toString()}`);
  },
};
