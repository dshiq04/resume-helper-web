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

/** 后端统一响应结构：成功 code=200，失败为 HTTP 错误码。 */
interface ApiResult<T> {
  code: number;
  message: string;
  data?: T;
}

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

function isApiResult<T>(payload: unknown): payload is ApiResult<T> {
  return (
    !!payload &&
    typeof payload === "object" &&
    typeof (payload as ApiResult<T>).code === "number" &&
    typeof (payload as ApiResult<T>).message === "string"
  );
}

async function parseBlobPayload(blob: Blob): Promise<unknown> {
  try {
    return JSON.parse(await blob.text());
  } catch {
    return undefined;
  }
}

/**
 * 所有 controller 模块共用的请求方法。
 * 后端统一返回 { code, message, data }：成功时只把 data 交给页面，
 * 失败时按 code + message 抛出 ApiError；401 时清理 token 并广播事件。
 */
export async function request<T>(path: string, options: RequestOptions = {}): Promise<T> {
  const { handleUnauthorized = true, ...config } = options;
  try {
    const response = await http.request<ApiResult<T> | T>({ url: path, ...config });
    if (config.responseType === "blob") {
      return response.data as T;
    }
    const payload = response.data;
    if (isApiResult<T>(payload)) {
      if (payload.code === 200) {
        return payload.data as T;
      }
      throw new ApiError(payload.message || "请求失败", payload.code, String(payload.code));
    }
    return payload as T;
  } catch (error) {
    if (error instanceof ApiError) {
      throw error;
    }
    if (!axios.isAxiosError(error)) {
      throw new ApiError("网络连接失败", 0, "network_error");
    }
    const status = error.response?.status ?? 0;
    let payload = error.response?.data as ApiResult<unknown> | { error?: string } | Blob | undefined;
    if (payload instanceof Blob) {
      payload = (await parseBlobPayload(payload)) as ApiResult<unknown> | undefined;
    }
    const body = payload as (ApiResult<unknown> & { error?: string }) | undefined;
    const code = typeof body?.code === "number" ? body.code : status;
    const message = body?.message ?? body?.error ?? "请求失败";
    if (status === 401 && handleUnauthorized) {
      clearToken();
      window.dispatchEvent(new CustomEvent("jobtrack:unauthorized"));
    }
    if (status === 0) {
      throw new ApiError("网络连接失败", 0, "network_error");
    }
    throw new ApiError(message, code, String(code));
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
