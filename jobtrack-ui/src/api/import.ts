import { downloadBlob, request } from "./client";
import type { ImportResult } from "./types";

/** 投递台账 Excel 导入。 */
export const importApi = {
  async downloadTemplate() {
    const blob = await request<Blob>("/applications/template", { responseType: "blob" });
    downloadBlob(blob, "投递导入模板.xlsx");
  },
  /** 按导入模板的列导出当前工作区的全部投递记录（注销账号前可用）。 */
  async exportAll() {
    const blob = await request<Blob>("/applications/export", { responseType: "blob" });
    downloadBlob(blob, "投递台账.xlsx");
  },
  importFile(file: File) {
    const data = new FormData();
    data.append("file", file);
    return request<ImportResult>("/applications/import", {
      method: "POST",
      data,
      timeout: 120_000,
    });
  },
};
