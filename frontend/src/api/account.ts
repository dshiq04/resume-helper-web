import { request } from "./client";
import type {
  AccessToken,
  AccessTokenInput,
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
  deleteToken(id: number) {
    return request<{ deleted: boolean }>(`/account/tokens/${id}`, { method: "DELETE" });
  },
};
