import { request } from "./client";
import type { Application, DeleteResult, TrashList } from "./types";

/** 对应后端 TrashController。 */
export const trashApi = {
  list() {
    return request<TrashList>("/trash");
  },
  restore(id: number) {
    return request<Application>(`/trash/${id}/restore`, { method: "POST" });
  },
  remove(id: number) {
    return request<DeleteResult>(`/trash/${id}`, { method: "DELETE" });
  },
  empty() {
    return request<DeleteResult>("/trash", { method: "DELETE" });
  },
};
