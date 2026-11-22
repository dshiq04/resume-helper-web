import { request } from "./client";
import type { Preferences } from "./types";

/** 对应后端 PreferencesController。 */
export const preferencesApi = {
  get() {
    return request<Preferences>("/system/preferences");
  },
  update(input: Partial<Preferences>) {
    return request<Preferences>("/system/preferences", { method: "PUT", data: input });
  },
};
