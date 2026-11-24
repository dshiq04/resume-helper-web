import { ApiError } from "../api/client";

export function errorMessage(error: unknown): string {
  if (error instanceof ApiError) {
    if (error.status === 0) {
      return "网络连接失败，请确认服务已启动";
    }
    return error.message;
  }
  if (error instanceof Error) {
    return error.message;
  }
  // Redux Toolkit 会把 thunk 异常序列化成普通对象，保留其 message 便于展示。
  if (error && typeof error === "object") {
    const status = (error as { status?: unknown }).status;
    if (status === 0) {
      return "网络连接失败，请确认服务已启动";
    }
    const message = (error as { message?: unknown }).message;
    if (typeof message === "string" && message.trim()) {
      return message;
    }
  }
  return "操作失败，请稍后再试";
}
