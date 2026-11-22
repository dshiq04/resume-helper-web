import dayjs from "dayjs";

export function formatDate(value?: string, withTime = false) {
  if (!value) return "—";
  const date = dayjs(value);
  if (!date.isValid()) return value;
  return withTime ? date.format("YYYY-MM-DD HH:mm") : date.format("YYYY-MM-DD");
}

export function formatShortDate(value?: string) {
  if (!value) return "—";
  const date = dayjs(value);
  return date.isValid() ? date.format("MM-DD") : value;
}

export function formatSize(bytes?: number) {
  if (!bytes || bytes <= 0) return "0 B";
  if (bytes < 1024) return `${bytes} B`;
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`;
  return `${(bytes / 1024 / 1024).toFixed(1)} MB`;
}

export function notifyDataChanged() {
  window.dispatchEvent(new CustomEvent("jobtrack:data-changed"));
}

export function openExternalLink(link: string) {
  if (!link) return;
  const target = /^[a-z][a-z0-9+.-]*:\/\//i.test(link) ? link : `https://${link}`;
  window.open(target, "_blank", "noopener,noreferrer");
}

export function yearOptions(extra = 3) {
  const current = new Date().getFullYear();
  return Array.from({ length: extra + 4 }, (_, index) => current + extra - index);
}
