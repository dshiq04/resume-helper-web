import { ApiError } from "../api/client";

const LANG_KEY = "jobtrack.lang";

/** 后端返回的 message 已经是可展示文案；只有本地兜底文案需要按语言翻译。 */
const LOCAL_MESSAGES: Record<string, { network: string; failed: string }> = {
  "zh-CN": { network: "网络连接失败，请确认服务已启动", failed: "操作失败，请稍后再试" },
  "zh-TW": { network: "網路連線失敗，請確認服務已啟動", failed: "操作失敗，請稍後再試" },
  en: { network: "Network error. Check that the service is running.", failed: "Operation failed. Please try again later." },
  ja: { network: "ネットワーク接続に失敗しました。サービスが起動しているか確認してください。", failed: "操作に失敗しました。しばらくしてから再試行してください。" },
  ko: { network: "네트워크 연결에 실패했습니다. 서비스가 실행 중인지 확인해 주세요.", failed: "작업에 실패했습니다. 잠시 후 다시 시도해 주세요." },
  ru: { network: "Ошибка сети. Проверьте, что сервис запущен.", failed: "Не удалось выполнить операцию. Повторите попытку позже." },
};

function localMessages() {
  const lang = typeof localStorage === "undefined"
    ? "zh-CN" : localStorage.getItem(LANG_KEY) ?? "zh-CN";
  return LOCAL_MESSAGES[lang] ?? LOCAL_MESSAGES["zh-CN"];
}

export function errorMessage(error: unknown): string {
  const messages = localMessages();
  if (error instanceof ApiError) {
    if (error.status === 0) {
      return messages.network;
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
      return messages.network;
    }
    const message = (error as { message?: unknown }).message;
    if (typeof message === "string" && message.trim()) {
      return message;
    }
  }
  return messages.failed;
}
