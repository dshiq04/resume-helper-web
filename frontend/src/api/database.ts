import { request } from "./client";
import type {
  DatabaseInfo,
  DatabaseSettings,
  DatabaseSwitchResult,
  DatabaseTestResult,
} from "./types";

/** 对应后端 DatabaseController。 */
export const databaseApi = {
  info() {
    return request<DatabaseInfo>("/system/database");
  },
  test(input: DatabaseSettings) {
    return request<DatabaseTestResult>("/system/database/test", {
      method: "POST",
      data: input,
      timeout: 30_000,
    });
  },
  switchDatabase(input: DatabaseSettings) {
    return request<DatabaseSwitchResult>("/system/database", {
      method: "PUT",
      data: input,
      timeout: 120_000,
    });
  },
};
