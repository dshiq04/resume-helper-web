import { request } from "./client";
import type { DatabaseInfo, DatabaseTestResult } from "./types";

/** 对应后端 DatabaseController：数据库配置只读，来源于 application-{profile}.yml。 */
export const databaseApi = {
  info() {
    return request<DatabaseInfo>("/system/database");
  },
  test() {
    return request<DatabaseTestResult>("/system/database/test", { timeout: 30_000 });
  },
};
