import { request } from "./client";
import type {
  AccessToken,
  AccessTokenInput,
  AccessTokenLogPage,
  AdminUser,
  AdminUserDetail,
  AdminUserPage,
  OnlineUser,
  PermissionItem,
  UserProfile,
} from "./types";

/** 管理员控制台接口，对应后端 /api/admin/**。 */
export const adminApi = {
  users(params: { search?: string; status?: string; page?: number; pageSize?: number } = {}) {
    const search = new URLSearchParams();
    search.set("page", String(params.page ?? 1));
    search.set("pageSize", String(params.pageSize ?? 20));
    if (params.search) search.set("search", params.search);
    if (params.status) search.set("status", params.status);
    return request<AdminUserPage>(`/admin/users?${search.toString()}`);
  },
  user(id: number) {
    return request<AdminUserDetail>(`/admin/users/${id}`);
  },
  updateUser(id: number, input: { username: string; displayName: string; tenantName: string }) {
    return request<AdminUser>(`/admin/users/${id}`, { method: "PUT", data: input });
  },
  saveProfile(id: number, input: UserProfile) {
    return request<UserProfile>(`/admin/users/${id}/profile`, { method: "PUT", data: input });
  },
  saveContact(id: number, input: { countryCode?: string; phone?: string; email?: string }) {
    return request<UserProfile>(`/admin/users/${id}/contact`, { method: "PUT", data: input });
  },
  uploadAvatar(id: number, file: File) {
    const data = new FormData();
    data.append("file", file);
    return request<UserProfile>(`/admin/users/${id}/avatar`, { method: "POST", data });
  },
  resetPassword(id: number, newPassword: string) {
    return request<AdminUser>(`/admin/users/${id}/password`, {
      method: "POST",
      data: { newPassword },
    });
  },
  updateStatus(
    id: number,
    input: {
      status: "NORMAL" | "CANCELLED" | "BANNED";
      banType?: "PERMANENT" | "TEMPORARY";
      banUntil?: string | null;
    },
  ) {
    return request<AdminUser>(`/admin/users/${id}/status`, {
      method: "PUT",
      data: input,
    });
  },
  deleteUser(id: number) {
    return request<{ deleted: boolean }>(`/admin/users/${id}`, { method: "DELETE" });
  },
  tokens(id: number) {
    return request<AccessToken[]>(`/admin/users/${id}/tokens`);
  },
  createToken(id: number, input: AccessTokenInput) {
    return request<AccessToken>(`/admin/users/${id}/tokens`, {
      method: "POST",
      data: input,
    });
  },
  updateTokenStatus(id: number, tokenId: number, enabled: boolean) {
    return request<AccessToken>(`/admin/users/${id}/tokens/${tokenId}/enabled`, {
      method: "PUT",
      data: { enabled },
    });
  },
  deleteToken(id: number, tokenId: number) {
    return request<{ deleted: boolean }>(`/admin/users/${id}/tokens/${tokenId}`, {
      method: "DELETE",
    });
  },
  tokenLogs(
    id: number,
    params: { page?: number; pageSize?: number; name?: string; result?: string } = {},
  ) {
    const search = new URLSearchParams();
    search.set("page", String(params.page ?? 1));
    search.set("pageSize", String(params.pageSize ?? 10));
    if (params.name) search.set("name", params.name);
    if (params.result) search.set("result", params.result);
    return request<AccessTokenLogPage>(`/admin/users/${id}/tokens/logs?${search.toString()}`);
  },
  online() {
    return request<OnlineUser[]>("/admin/online");
  },
  kick(id: number) {
    return request<{ kicked: boolean; sessions: number }>(`/admin/online/${id}/kick`, {
      method: "POST",
    });
  },
  permissions() {
    return request<PermissionItem[]>("/admin/permissions");
  },
  userPermissions(id: number) {
    return request<string[]>(`/admin/users/${id}/permissions`);
  },
  grant(id: number, code: string) {
    return request<AdminUser>(`/admin/users/${id}/permissions`, {
      method: "POST",
      data: { code },
    });
  },
  revoke(id: number, code: string) {
    return request<AdminUser>(`/admin/users/${id}/permissions/${encodeURIComponent(code)}`, {
      method: "DELETE",
    });
  },
};
