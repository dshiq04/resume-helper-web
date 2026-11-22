import { request } from "./client";
import type { SystemInfo } from "./types";

/** 对应后端 SystemController。 */
export const systemApi = {
  health() {
    return request<{ status: string; version: string; time: string }>("/health", {
      handleUnauthorized: false,
    });
  },
  info() {
    return request<SystemInfo>("/system/info");
  },
};
