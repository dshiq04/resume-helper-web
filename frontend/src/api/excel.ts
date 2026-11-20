import { downloadBlob, request } from "./client";
import type { ImportResult } from "./types";

/** 对应后端 ExcelController。 */
export const excelApi = {
  importFile(file: File) {
    const data = new FormData();
    data.append("file", file);
    return request<ImportResult>("/applications/import", { method: "POST", data });
  },
  async downloadTemplate() {
    const blob = await request<Blob>("/applications/template", { responseType: "blob" });
    downloadBlob(blob, "投递导入模板.xlsx");
  },
};
