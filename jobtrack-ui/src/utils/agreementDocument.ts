import dayjs from "dayjs";
import type { Lang } from "../i18n";

interface AgreementLabels {
  title: string;
  signer: string;
  userId: string;
  signedAt: string;
  location: string;
  language: string;
  timezone: string;
  exportedAt: string;
  unsigned: string;
  notRecorded: string;
  print: string;
}

const LABELS: Record<Lang, AgreementLabels> = {
  "zh-CN": {
    title: "用户协议",
    signer: "签署用户",
    userId: "用户 ID",
    signedAt: "签署时间",
    location: "签署地点",
    language: "签署语言",
    timezone: "时区",
    exportedAt: "导出时间",
    unsigned: "未签署",
    notRecorded: "未记录",
    print: "打印 / 导出 PDF",
  },
  "zh-TW": {
    title: "使用者協議",
    signer: "簽署使用者",
    userId: "使用者 ID",
    signedAt: "簽署時間",
    location: "簽署地點",
    language: "簽署語言",
    timezone: "時區",
    exportedAt: "匯出時間",
    unsigned: "未簽署",
    notRecorded: "未記錄",
    print: "列印 / 匯出 PDF",
  },
  en: {
    title: "User Agreement",
    signer: "Signed by",
    userId: "User ID",
    signedAt: "Signed at",
    location: "Location",
    language: "Language",
    timezone: "Time zone",
    exportedAt: "Exported at",
    unsigned: "Not signed",
    notRecorded: "Not recorded",
    print: "Print / Export PDF",
  },
  ja: {
    title: "利用規約",
    signer: "署名ユーザー",
    userId: "ユーザー ID",
    signedAt: "署名日時",
    location: "署名場所",
    language: "署名言語",
    timezone: "タイムゾーン",
    exportedAt: "エクスポート日時",
    unsigned: "未署名",
    notRecorded: "記録なし",
    print: "印刷 / PDF 出力",
  },
  ko: {
    title: "이용약관",
    signer: "서명 사용자",
    userId: "사용자 ID",
    signedAt: "서명 시간",
    location: "서명 장소",
    language: "서명 언어",
    timezone: "시간대",
    exportedAt: "내보낸 시간",
    unsigned: "미서명",
    notRecorded: "기록 없음",
    print: "인쇄 / PDF 내보내기",
  },
  ru: {
    title: "Пользовательское соглашение",
    signer: "Подписал",
    userId: "ID пользователя",
    signedAt: "Время подписания",
    location: "Место",
    language: "Язык",
    timezone: "Часовой пояс",
    exportedAt: "Время экспорта",
    unsigned: "Не подписано",
    notRecorded: "Не указано",
    print: "Печать / Экспорт в PDF",
  },
};

const LANGUAGE_NAMES: Record<Lang, string> = {
  "zh-CN": "简体中文",
  "zh-TW": "繁體中文",
  en: "English",
  ja: "日本語",
  ko: "한국어",
  ru: "Русский",
};

export interface AgreementDocumentData {
  language: Lang;
  bodyHtml: string;
  signerName?: string | null;
  username?: string | null;
  userId: number;
  signedAt?: string | null;
  location?: string | null;
  timezone?: string | null;
}

export function resolveAgreementLanguage(value?: string | null): Lang {
  if (
    value === "zh-CN"
    || value === "zh-TW"
    || value === "en"
    || value === "ja"
    || value === "ko"
    || value === "ru"
  ) {
    return value;
  }
  return "zh-CN";
}

export function agreementLanguageName(value?: string | null): string {
  return LANGUAGE_NAMES[resolveAgreementLanguage(value)];
}

function escapeHtml(value?: string | null): string {
  return (value ?? "")
    .replace(/&/g, "&amp;")
    .replace(/</g, "&lt;")
    .replace(/>/g, "&gt;")
    .replace(/"/g, "&quot;")
    .replace(/'/g, "&#39;");
}

function formatTime(value?: string | null): string {
  return value ? dayjs(value).format("YYYY-MM-DD HH:mm:ss") : "";
}

/** 生成可独立打开的协议存档 HTML，正文使用签署语言。 */
export function buildAgreementDocumentHtml(data: AgreementDocumentData): string {
  const lang = resolveAgreementLanguage(data.language);
  const labels = LABELS[lang];
  const exportedAt = dayjs().format("YYYY-MM-DD HH:mm:ss");
  const signer = data.signerName || data.username || labels.notRecorded;
  const username = data.username ? ` (@${escapeHtml(data.username)})` : "";
  const location = data.location || data.timezone || labels.notRecorded;
  const meta = [
    [labels.signer, `${escapeHtml(signer)}${username}`],
    [labels.userId, String(data.userId)],
    [labels.signedAt, data.signedAt ? formatTime(data.signedAt) : labels.unsigned],
    [labels.location, escapeHtml(location)],
    [labels.language, `${LANGUAGE_NAMES[lang]} (${lang})`],
    [labels.timezone, escapeHtml(data.timezone || labels.notRecorded)],
    [labels.exportedAt, exportedAt],
  ]
    .map(([label, value]) => (
      `<div class="meta-item"><span class="meta-label">${label}</span>`
      + `<span class="meta-value">${value}</span></div>`
    ))
    .join("");

  return `<!doctype html>
<html lang="${lang}">
<head>
<meta charset="utf-8" />
<meta name="viewport" content="width=device-width, initial-scale=1" />
<title>${labels.title} · ${escapeHtml(data.username || String(data.userId))}</title>
<style>
  :root { color-scheme: light; }
  * { box-sizing: border-box; }
  body {
    margin: 0;
    padding: 32px 36px 48px;
    color: #20212a;
    background: #fff;
    font-family: "Noto Sans SC", "PingFang SC", "Microsoft YaHei", "Segoe UI", sans-serif;
    font-size: 13px;
    line-height: 1.85;
  }
  .toolbar {
    display: flex;
    justify-content: flex-end;
    margin-bottom: 20px;
  }
  .toolbar button {
    padding: 8px 16px;
    border: 1px solid #4c56d8;
    border-radius: 7px;
    background: #4c56d8;
    color: #fff;
    font-size: 13px;
    cursor: pointer;
  }
  h1 {
    margin: 0 0 6px;
    font-size: 24px;
    letter-spacing: 0.02em;
  }
  .subtitle {
    margin: 0 0 24px;
    color: #6b6c78;
    font-size: 12px;
  }
  .meta {
    display: grid;
    grid-template-columns: repeat(2, minmax(0, 1fr));
    gap: 6px 24px;
    margin-bottom: 26px;
    padding: 14px 16px;
    border: 1px solid #dcdee8;
    border-radius: 10px;
    background: #f7f8fc;
  }
  .meta-item {
    display: flex;
    gap: 8px;
    min-width: 0;
  }
  .meta-label {
    flex: 0 0 auto;
    color: #6b6c78;
  }
  .meta-value {
    min-width: 0;
    font-weight: 600;
    overflow-wrap: anywhere;
  }
  .agreement-body h2 {
    margin: 24px 0 10px;
    font-size: 15px;
    line-height: 1.5;
  }
  .agreement-body h2:first-child { margin-top: 0; }
  .agreement-body p,
  .agreement-body ul { margin: 0 0 12px; }
  .agreement-body ul { padding-left: 20px; }
  .agreement-body li { margin-bottom: 6px; }
  .agreement-body strong { color: #b42318; }
  .signature {
    margin-top: 34px;
    padding-top: 18px;
    border-top: 1px dashed #c9cbd6;
    color: #4b4c58;
    font-size: 12px;
  }
  .signature strong { color: #20212a; }
  @page { size: A4; margin: 16mm; }
  @media print {
    body { padding: 0; }
    .toolbar { display: none; }
    .meta { break-inside: avoid; }
  }
</style>
</head>
<body>
  <div class="toolbar"><button type="button" onclick="window.print()">${labels.print}</button></div>
  <h1>${labels.title}</h1>
  <p class="subtitle">${escapeHtml(data.username || "")}${data.userId ? ` · ID ${data.userId}` : ""}</p>
  <section class="meta">${meta}</section>
  <main class="agreement-body">${data.bodyHtml}</main>
  <section class="signature">
    <div>${labels.signer}: <strong>${escapeHtml(signer)}</strong>${username}</div>
    <div>${labels.userId}: <strong>${data.userId}</strong></div>
    <div>${labels.signedAt}: <strong>${data.signedAt ? formatTime(data.signedAt) : labels.unsigned}</strong></div>
    <div>${labels.location}: <strong>${escapeHtml(location)}</strong></div>
    <div>${labels.exportedAt}: <strong>${exportedAt}</strong></div>
  </section>
</body>
</html>`;
}

/** 导出独立 HTML 存档。 */
export function downloadAgreementHtml(data: AgreementDocumentData) {
  const html = buildAgreementDocumentHtml(data);
  const blob = new Blob([html], { type: "text/html;charset=utf-8" });
  const link = document.createElement("a");
  const name = (data.username || data.userId).toString().replace(/[^\w.-]+/g, "-");
  link.download = `agreement-${name}-${dayjs().format("YYYYMMDD-HHmmss")}.html`;
  link.href = URL.createObjectURL(blob);
  document.body.appendChild(link);
  link.click();
  link.remove();
  window.setTimeout(() => URL.revokeObjectURL(link.href), 2000);
}

/** 打开打印窗口；在打印对话框中选择“另存为 PDF”即可导出 PDF。 */
export function printAgreementPdf(data: AgreementDocumentData): boolean {
  const html = buildAgreementDocumentHtml(data);
  const iframe = document.createElement("iframe");
  iframe.setAttribute("aria-hidden", "true");
  iframe.style.cssText = "position:fixed;right:0;bottom:0;width:0;height:0;border:0;";
  document.body.appendChild(iframe);
  const frameWindow = iframe.contentWindow;
  const frameDocument = frameWindow?.document;
  if (!frameWindow || !frameDocument) {
    iframe.remove();
    return false;
  }
  frameDocument.open();
  frameDocument.write(html);
  frameDocument.close();
  const invoke = () => {
    try {
      frameWindow.focus();
      frameWindow.print();
    } finally {
      window.setTimeout(() => iframe.remove(), 1500);
    }
  };
  window.setTimeout(invoke, 300);
  return true;
}
