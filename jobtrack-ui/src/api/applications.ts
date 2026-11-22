import { request } from "./client";
import type {
  Application,
  ApplicationInput,
  ApplicationList,
  ApplicationUpdate,
  Category,
  DeleteResult,
  HistoryItem,
  HistoryResponse,
  ListParams,
  Status,
  YearList,
} from "./types";

/** 对应后端 ApplicationController。 */
export const applicationApi = {
  list(params: ListParams) {
    const query: Record<string, string | number> = {
      page: params.page ?? 1,
      pageSize: params.pageSize ?? 10,
    };
    if (params.search) query.search = params.search;
    if (params.category && params.category !== "all") query.category = params.category;
    if (params.status && params.status !== "all") query.status = params.status;
    if (params.channel) query.channel = params.channel;
    if (params.priority) query.priority = params.priority;
    if (params.year && params.year !== "all") query.year = params.year;
    if (params.from) query.from = params.from;
    if (params.to) query.to = params.to;
    return request<ApplicationList>("/applications", { params: query });
  },
  years() {
    return request<YearList>("/applications/years");
  },
  create(input: ApplicationInput) {
    return request<Application>("/applications", { method: "POST", data: input });
  },
  get(id: number) {
    return request<Application>(`/application/${id}`);
  },
  update(id: number, input: ApplicationUpdate) {
    return request<Application>(`/application/${id}`, { method: "PUT", data: input });
  },
  remove(id: number) {
    return request<DeleteResult>(`/application/${id}`, { method: "DELETE" });
  },
  batchDelete(ids: number[]) {
    return request<DeleteResult>("/applications/batch-delete", {
      method: "POST",
      data: { ids },
    });
  },
  batchStatus(ids: number[], status: Status) {
    return request<{ updated: number }>("/applications/batch-status", {
      method: "PATCH",
      data: { ids, status },
    });
  },
  batchCategory(ids: number[], category: Category) {
    return request<{ updated: number }>("/applications/batch-category", {
      method: "PATCH",
      data: { ids, category },
    });
  },
  companies(search?: string) {
    return request<{ items: string[] }>("/applications/companies", {
      params: search ? { search } : undefined,
    });
  },
  history(id: number) {
    return request<HistoryResponse>(`/application/${id}/history`);
  },
  appendHistory(id: number, status: Status, changedAt: string) {
    return request<{ items: HistoryItem[] }>(`/application/${id}/history`, {
      method: "POST",
      data: { status, changedAt },
    });
  },
};
