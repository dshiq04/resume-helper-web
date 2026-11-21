const DEFAULT_HOST = "http://127.0.0.1";
const DEFAULT_PORT = "18080";
const SERVER_KEY = "jobtrack.serverUrl";
const CODE_KEY = "jobtrack.accessCode";

const state = {
  serverUrl: `${DEFAULT_HOST}:${DEFAULT_PORT}`,
  accessCode: "",
};

const $ = (id) => document.getElementById(id);

function normalizeServer(host, port) {
  let base = (host || DEFAULT_HOST).trim();
  if (!/^https?:\/\//i.test(base)) {
    base = `http://${base}`;
  }
  base = base.replace(/\/+$/, "").replace(/:\d+$/, "");
  const value = (port || DEFAULT_PORT).toString().trim() || DEFAULT_PORT;
  return `${base}:${value}`;
}

function parseServer(url) {
  try {
    const parsed = new URL(url);
    return {
      host: `${parsed.protocol}//${parsed.hostname}`,
      port: parsed.port || DEFAULT_PORT,
    };
  } catch {
    return { host: DEFAULT_HOST, port: DEFAULT_PORT };
  }
}

function setStatus(text, ok = true) {
  const node = $("status");
  node.innerHTML = `<span class="dot" style="background:${ok ? "#7fb88a" : "#c2703d"}"></span>${text}`;
}

function showError(message) {
  const node = $("connect-error");
  node.textContent = message || "";
  node.hidden = !message;
}

function scopeLabel(scope) {
  if (scope === "read") return "只读";
  if (scope === "write") return "只写";
  return "读写";
}

async function load() {
  const stored = await chrome.storage.local.get([SERVER_KEY, CODE_KEY]);
  state.serverUrl = stored[SERVER_KEY] || state.serverUrl;
  state.accessCode = stored[CODE_KEY] || "";
  const { host, port } = parseServer(state.serverUrl);
  $("server-host").value = host;
  $("server-port").value = port;
  $("access-code").value = state.accessCode;
  $("open-web").href = state.serverUrl;
  if (state.accessCode) {
    await validate(true);
  }
}

async function validate(silent = false) {
  const code = $("access-code").value.trim();
  if (!code) {
    setStatus("请填写授权码", false);
    return false;
  }
  try {
    const response = await fetch(`${state.serverUrl}/api/tokens/validate`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ code }),
    });
    let data = null;
    try {
      data = await response.json();
    } catch {
      data = null;
    }
    if (!response.ok || !data || !data.valid) {
      if (!silent) {
        const reason = data && data.reason;
        const message = response.status === 410 || reason === "expired"
          ? "授权码已过期"
          : response.status === 403 || reason === "disabled"
            ? "授权码已停用或账号状态异常"
            : "授权码无效或已被删除";
        showError(message);
        setStatus(message, false);
      }
      return false;
    }
    if (data.scope !== "read" && data.scope !== "readwrite") {
      showError("该授权码为只写权限，无法读取数据；请使用读或读写权限");
      setStatus("只写授权码不可用", false);
      return false;
    }
    showError("");
    setStatus(
      `连接正常 · ${data.displayName || data.username} · ${scopeLabel(data.scope)}`
        + (data.expiresOn ? ` · 有效期至 ${data.expiresOn}` : " · 长期有效"),
      true,
    );
    return true;
  } catch {
    if (!silent) {
      showError("无法连接服务端，请检查服务器与端口");
    }
    setStatus("无法连接服务端", false);
    return false;
  }
}

async function save(event) {
  event.preventDefault();
  showError("");
  state.serverUrl = normalizeServer($("server-host").value, $("server-port").value);
  state.accessCode = $("access-code").value.trim();
  $("open-web").href = state.serverUrl;
  const ok = await validate();
  if (!ok) {
    return;
  }
  await chrome.storage.local.set({
    [SERVER_KEY]: state.serverUrl,
    [CODE_KEY]: state.accessCode,
  });
  setStatus($("status").textContent + " · 已保存", true);
}

$("connect-form").addEventListener("submit", save);
$("clear").addEventListener("click", async () => {
  state.accessCode = "";
  $("access-code").value = "";
  await chrome.storage.local.set({ [CODE_KEY]: "" });
  showError("");
  setStatus("已清除授权码", false);
});

void load();
