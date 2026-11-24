/**
 * 应用名称与版本号统一取自前端 .env：
 * VITE_APP_TITLE / VITE_APP_VERSION。
 */
export const APP_NAME = import.meta.env.VITE_APP_TITLE || "履途 ResumeTrail";

export const APP_VERSION = (import.meta.env.VITE_APP_VERSION || "").trim();

/** 统一展示为 V1.2.1，兼容 .env 中填写 1.2.1 / v1.2.1 / V1.2.1。 */
export function appVersionLabel() {
  if (!APP_VERSION) {
    return "V—";
  }
  const match = /^v(.+)$/i.exec(APP_VERSION);
  return match ? `V${match[1]}` : `V${APP_VERSION}`;
}
