import { downloadBlob, request } from "./client";
import type { Category, ImportResult } from "./types";

/** 投递台账 Excel 导入与导出。 */
export const importApi = {
  async downloadTemplate() {
    const blob = await request<Blob>("/applications/template", { responseType: "blob" });
    downloadBlob(blob, "投递导入模板.xlsx");
  },
  /**
   * 按投递类型与投递时间范围导出投递台账。
   * 不传筛选条件时导出全部记录。
   */
  async exportApplications(params: { categories?: Category[]; from?: string; to?: string } = {}) {
    const search = new URLSearchParams();
    params.categories?.forEach((category) => search.append("categories", category));
    if (params.from) search.set("from", params.from);
    if (params.to) search.set("to", params.to);
    const suffix = search.toString();
    const blob = await request<Blob>(
      `/applications/export${suffix ? `?${suffix}` : ""}`,
      { responseType: "blob" },
    );
    downloadBlob(blob, "投递台账.xlsx");
  },
  /** 按导入模板的列导出当前工作区的全部投递记录（注销账号前可用）。 */
  async exportAll() {
    await importApi.exportApplications();
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
