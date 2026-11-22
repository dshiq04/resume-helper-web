import { request } from "./client";
import type { DeleteResult, Resume, ResumeList } from "./types";

/** 对应后端 ResumeController。 */
export const resumeApi = {
  list(search?: string) {
    return request<ResumeList>("/resumes", {
      params: search ? { search } : undefined,
    });
  },
  create(name: string, tags: string[], file: File) {
    const data = new FormData();
    data.append("name", name);
    data.append("tags", JSON.stringify(tags));
    data.append("file", file);
    return request<Resume>("/resumes", { method: "POST", data, timeout: 120_000 });
  },
  update(id: number, input: { name?: string; tags?: string[]; pinned?: boolean }) {
    return request<Resume>(`/resume/${id}`, { method: "PUT", data: input });
  },
  remove(id: number) {
    return request<DeleteResult>(`/resume/${id}`, { method: "DELETE" });
  },
  fetchFile(id: number) {
    return request<Blob>(`/resume/${id}/file`, { responseType: "blob", timeout: 120_000 });
  },
};
