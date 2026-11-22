const DEVICE_ID_KEY = "jobtrack.device.id";

export type PlatformKind = "desktop" | "tablet" | "mobile";

export interface ClientInfo {
  platform: PlatformKind;
  system: string;
}

interface UserAgentData {
  platform?: string;
  getHighEntropyValues?: (hints: string[]) => Promise<{ platformVersion?: string }>;
}

let fallbackDeviceId: string | null = null;

function randomHex(bytes: number) {
  const data = new Uint8Array(bytes);
  crypto.getRandomValues(data);
  return Array.from(data, (value) => value.toString(16).padStart(2, "0")).join("");
}

/**
 * 设备标识码：浏览器内持久化，用于标识当前设备。
 * localStorage 不可用时退化为当前会话内的随机值。
 */
export function getDeviceId(): string {
  if (fallbackDeviceId) {
    return fallbackDeviceId;
  }
  try {
    const saved = localStorage.getItem(DEVICE_ID_KEY);
    if (saved && /^[0-9a-f]{64}$/i.test(saved)) {
      return saved;
    }
    const created = randomHex(32);
    localStorage.setItem(DEVICE_ID_KEY, created);
    fallbackDeviceId = created;
    return created;
  } catch {
    fallbackDeviceId = randomHex(32);
    return fallbackDeviceId;
  }
}

function detectPlatform(userAgent: string): PlatformKind {
  const touchPoints = navigator.maxTouchPoints ?? 0;
  if (
    /iPad|Tablet|PlayBook|Silk/i.test(userAgent) ||
    (/Android/i.test(userAgent) && !/Mobile/i.test(userAgent)) ||
    (/Macintosh/i.test(userAgent) && touchPoints > 1)
  ) {
    return "tablet";
  }
  if (/Mobi|iPhone|iPod|Android.*Mobile|Windows Phone/i.test(userAgent)) {
    return "mobile";
  }
  return "desktop";
}

function iosVersion(userAgent: string): string {
  const match = userAgent.match(/(?:iPhone )?OS (\d+)[_.](\d+)/);
  if (!match) {
    return "";
  }
  return ` ${match[1]}.${match[2]}`;
}

function androidVersion(userAgent: string): string {
  const match = userAgent.match(/Android\s+([\d.]+)/);
  return match ? ` ${match[1]}` : "";
}

/** 当前客户端系统信息。优先使用 User-Agent Client Hints，
 * 老旧浏览器则回退到 userAgent / platform 解析。
 */
export async function getClientInfo(): Promise<ClientInfo> {
  const userAgent = navigator.userAgent;
  const uaData = (navigator as Navigator & { userAgentData?: UserAgentData }).userAgentData;
  let platformVersion = "";
  try {
    const values = await uaData?.getHighEntropyValues?.(["platformVersion"]);
    platformVersion = values?.platformVersion ?? "";
  } catch {
    // 部分浏览器不允许读取高熵信息，忽略即可。
  }

  const platform = detectPlatform(userAgent);
  const uaPlatform = uaData?.platform || navigator.platform || "";
  const windowsMajor = Number(platformVersion.split(".")[0]);

  if (/Windows/i.test(uaPlatform) || /Windows/i.test(userAgent)) {
    // Windows 11 的 UA-CH platformVersion 主版本号从 13 开始。
    const version = Number.isFinite(windowsMajor) && windowsMajor >= 13 ? "11" : "10";
    return { platform, system: `Windows ${version}` };
  }
  if (/Android/i.test(userAgent)) {
    return { platform, system: `Android${androidVersion(userAgent)}` };
  }
  if (/iPhone|iPad|iPod/i.test(userAgent)) {
    const name = /iPad/i.test(userAgent) ? "iPadOS" : "iOS";
    return { platform, system: `${name}${iosVersion(userAgent)}` };
  }
  if (/Mac OS X|Macintosh/i.test(userAgent) || /Mac/i.test(uaPlatform)) {
    const version = platformVersion && !platformVersion.startsWith("0") ? ` ${platformVersion}` : "";
    return { platform, system: `macOS${version}` };
  }
  if (/CrOS/i.test(userAgent)) {
    return { platform, system: "ChromeOS" };
  }
  if (/Linux/i.test(uaPlatform) || /Linux/i.test(userAgent)) {
    const distro = userAgent.match(/(Ubuntu|Debian|Fedora|CentOS|Arch)/i)?.[1];
    return { platform, system: distro ? `Linux (${distro})` : "Linux" };
  }
  return { platform, system: uaPlatform || "未知系统" };
}

/** 当前浏览器时区，签署记录与导出协议时使用。 */
export function getTimeZone(): string {
  try {
    return Intl.DateTimeFormat().resolvedOptions().timeZone || "";
  } catch {
    return "";
  }
}

/** 当前浏览器语言标识，用于服务端在缺少签署语言时兜底。 */
export function getLocale(): string {
  return navigator.language || "";
}

/** 复制文本，兼容非安全上下文（如通过局域网 HTTP 访问）。 */
export async function copyText(text: string): Promise<void> {
  if (navigator.clipboard?.writeText) {
    await navigator.clipboard.writeText(text);
    return;
  }
  const textarea = document.createElement("textarea");
  textarea.value = text;
  textarea.style.position = "fixed";
  textarea.style.opacity = "0";
  document.body.appendChild(textarea);
  textarea.select();
  document.execCommand("copy");
  textarea.remove();
}
