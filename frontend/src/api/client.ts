import axios, { type AxiosRequestConfig } from "axios";

const TOKEN_KEY = "jobtrack.token";

const API_BASE = (import.meta.env.VITE_API_BASE_URL || "/api").replace(/\/+$/, "");

const http = axios.create({
  baseURL: API_BASE,
  timeout: 30_000,
  headers: { Accept: "application/json" },
});

http.interceptors.request.use((config) => {
  const token = localStorage.getItem(TOKEN_KEY);
  if (token) {
    config.headers.set("Authorization", `Bearer ${token}`);
  }
  return config;
});

export class ApiError extends Error {
  status: number;
  code?: string;

  constructor(message: string, status: number, code?: string) {
    super(message);
    this.name = "ApiError";
    this.status = status;
    this.code = code;
  }
}

export function getToken() {
  return localStorage.getItem(TOKEN_KEY) ?? "";
}

export function setToken(token: string) {
  localStorage.setItem(TOKEN_KEY, token);
}

export function clearToken() {
  localStorage.removeItem(TOKEN_KEY);
}

export interface RequestOptions extends AxiosRequestConfig {
  handleUnauthorized?: boolean;
}

/**
 * 所有 controller 模块共用的请求方法。
 * 401 时清理 token 并广播事件，由 AuthProvider 统一退出登录。
 */
export async function request<T>(path: string, options: RequestOptions = {}): Promise<T> {
  const { handleUnauthorized = true, ...config } = options;
  try {
    const response = await http.request<T>({ url: path, ...config });
    return response.data;
  } catch (error) {
    if (!axios.isAxiosError(error)) {
      throw new ApiError("网络连接失败", 0, "network_error");
    }
    const status = error.response?.status ?? 0;
    let payload = error.response?.data as { error?: string; code?: string } | Blob | undefined;
    if (payload instanceof Blob) {
      try {
        payload = JSON.parse(await payload.text());
      } catch {
        payload = undefined;
      }
    }
    const body = payload as { error?: string; code?: string } | undefined;
    if (status === 401 && handleUnauthorized && body?.code !== "invalid_credentials") {
      clearToken();
      window.dispatchEvent(new CustomEvent("jobtrack:unauthorized"));
    }
    if (status === 0) {
      throw new ApiError("网络连接失败", 0, "network_error");
    }
    throw new ApiError(body?.error ?? "请求失败", status, body?.code);
  }
}

export function downloadBlob(blob: Blob, fileName: string) {
  const url = URL.createObjectURL(blob);
  const anchor = document.createElement("a");
  anchor.href = url;
  anchor.download = fileName;
  document.body.appendChild(anchor);
  anchor.click();
  anchor.remove();
  window.setTimeout(() => URL.revokeObjectURL(url), 1000);
}
