import { downloadBlob, request } from "./client";
import type { BackupImportResult } from "./types";

/** 对应后端 BackupController。 */
export const backupApi = {
  async exportData() {
    const blob = await request<Blob>("/system/backup/export", {
      responseType: "blob",
      timeout: 120_000,
    });
    const stamp = new Date().toISOString().slice(0, 16).replace(/[:T]/g, "-");
    downloadBlob(blob, `jobtrack-backup-${stamp}.zip`);
  },
  importData(file: File, includeTrash = false) {
    const data = new FormData();
    data.append("file", file);
    return request<BackupImportResult>(
      `/system/backup/import?includeTrash=${includeTrash}`,
      { method: "POST", data, timeout: 120_000 },
    );
  },
};
