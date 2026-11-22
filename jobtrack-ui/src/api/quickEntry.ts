import { request } from "./client";
import type { QuickEntryDefaults } from "./types";

/** 对应后端 QuickEntryController。 */
export const quickEntryApi = {
  defaults() {
    return request<QuickEntryDefaults>("/system/quick-entry/defaults");
  },
};
