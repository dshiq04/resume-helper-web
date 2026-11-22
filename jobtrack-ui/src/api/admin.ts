import { request } from "./client";
import type {
  AccessToken,
  AccessTokenInput,
  AccessTokenLogPage,
  AdminUser,
  AdminUserDetail,
  AdminUserPage,
  Application,
  ApplicationInput,
  ApplicationList,
  ApplicationUpdate,
  DeleteResult,
  OnlineUser,
  PermissionItem,
  Resume,
  ResumeList,
  HistoryResponse,
  Status,
  UserAgreementRecord,
  UserOperationPage,
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
  /** 用户协议签署记录：管理员查看状态并按签署语言导出。 */
  userAgreement(id: number) {
    return request<UserAgreementRecord>(`/admin/users/${id}/agreement`);
  },
  /** 用户操作记录：按用户、类型与时间范围分页查询。 */
  operations(
    params: {
      userId?: number;
      type?: string;
      from?: string;
      to?: string;
      keyword?: string;
      page?: number;
      pageSize?: number;
    } = {},
  ) {
    const search = new URLSearchParams();
    search.set("page", String(params.page ?? 1));
    search.set("pageSize", String(params.pageSize ?? 20));
    if (params.userId) search.set("userId", String(params.userId));
    if (params.type && params.type !== "ALL") search.set("type", params.type);
    if (params.from) search.set("from", params.from);
    if (params.to) search.set("to", params.to);
    if (params.keyword) search.set("keyword", params.keyword);
    return request<UserOperationPage>(`/admin/operations?${search.toString()}`);
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
      reason?: string;
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
  applications(
    id: number,
    params: {
      search?: string;
      category?: string;
      status?: string;
      channel?: string;
      priority?: string;
      year?: number;
      page?: number;
      pageSize?: number;
    } = {},
  ) {
    const search = new URLSearchParams();
    search.set("page", String(params.page ?? 1));
    search.set("pageSize", String(params.pageSize ?? 10));
    if (params.search) search.set("search", params.search);
    if (params.category && params.category !== "all") search.set("category", params.category);
    if (params.status && params.status !== "all") search.set("status", params.status);
    if (params.channel) search.set("channel", params.channel);
    if (params.priority) search.set("priority", params.priority);
    if (params.year) search.set("year", String(params.year));
    return request<ApplicationList>(`/admin/users/${id}/applications?${search.toString()}`);
  },
  createApplication(id: number, input: ApplicationInput) {
    return request<Application>(`/admin/users/${id}/applications`, {
      method: "POST",
      data: input,
    });
  },
  application(id: number, applicationId: number) {
    return request<Application>(`/admin/users/${id}/applications/${applicationId}`);
  },
  applicationHistory(id: number, applicationId: number) {
    return request<HistoryResponse>(`/admin/users/${id}/applications/${applicationId}/history`);
  },
  appendApplicationHistory(
    id: number,
    applicationId: number,
    input: { status: Status; changedAt: string },
  ) {
    return request<{ items: unknown[] }>(
      `/admin/users/${id}/applications/${applicationId}/history`,
      { method: "POST", data: input },
    );
  },
  updateApplication(id: number, applicationId: number, input: ApplicationUpdate) {
    return request<Application>(`/admin/users/${id}/applications/${applicationId}`, {
      method: "PUT",
      data: input,
    });
  },
  deleteApplication(id: number, applicationId: number) {
    return request<DeleteResult>(`/admin/users/${id}/applications/${applicationId}`, {
      method: "DELETE",
    });
  },
  applicationCompanies(id: number, search?: string) {
    const query = new URLSearchParams();
    if (search) query.set("search", search);
    const suffix = query.toString() ? `?${query.toString()}` : "";
    return request<{ items: string[] }>(`/admin/users/${id}/applications/companies${suffix}`);
  },
  resumes(id: number, search?: string) {
    const query = new URLSearchParams();
    if (search) query.set("search", search);
    const suffix = query.toString() ? `?${query.toString()}` : "";
    return request<ResumeList>(`/admin/users/${id}/resumes${suffix}`);
  },
  createResume(id: number, name: string, tags: string[], file: File) {
    const data = new FormData();
    data.append("name", name);
    data.append("tags", JSON.stringify(tags));
    data.append("file", file);
    return request<Resume>(`/admin/users/${id}/resumes`, {
      method: "POST",
      data,
      timeout: 120_000,
    });
  },
  updateResume(id: number, resumeId: number, input: { name?: string; tags?: string[]; pinned?: boolean }) {
    return request<Resume>(`/admin/users/${id}/resumes/${resumeId}`, {
      method: "PUT",
      data: input,
    });
  },
  deleteResume(id: number, resumeId: number) {
    return request<DeleteResult>(`/admin/users/${id}/resumes/${resumeId}`, {
      method: "DELETE",
    });
  },
  resumeFile(id: number, resumeId: number) {
    return request<Blob>(`/admin/users/${id}/resumes/${resumeId}/file`, {
      responseType: "blob",
    });
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
  permissionUsers(search?: string) {
    const query = new URLSearchParams();
    query.set("page", "1");
    query.set("pageSize", "200");
    if (search) query.set("search", search);
    return request<AdminUserPage>(`/admin/permissions/users?${query.toString()}`);
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
