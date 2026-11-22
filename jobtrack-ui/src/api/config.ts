import { request } from "./client";
import type { PublicConfig } from "./types";

/** 对应后端 PublicConfigController。 */
export const configApi = {
  get() {
    return request<PublicConfig>("/config", { handleUnauthorized: false });
  },
};
