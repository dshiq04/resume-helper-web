const DEFAULT_SERVER = "http://127.0.0.1:18080";
const TOKEN_KEY = "jobtrack.token";
const SERVER_KEY = "jobtrack.serverUrl";

const state = {
  serverUrl: DEFAULT_SERVER,
  token: "",
  captchaEnabled: false,
  captcha: null,
};

const $ = (id) => document.getElementById(id);

function normalizeServer(value) {
  return (value || DEFAULT_SERVER).trim().replace(/\/+$/, "") || DEFAULT_SERVER;
}

function setStatus(text, ok = true) {
  const node = $("server-status");
  node.innerHTML = `<span class="dot" style="background:${ok ? "#7fb88a" : "#c2703d"}"></span>${text}`;
}

function showError(message) {
  const node = $("login-error");
  node.textContent = message || "";
  node.hidden = !message;
}

async function api(path, options = {}) {
  const headers = { Accept: "application/json", ...(options.headers || {}) };
  if (state.token) headers.Authorization = `Bearer ${state.token}`;
  const response = await fetch(`${state.serverUrl}/api${path}`, { ...options, headers });
  let data = null;
  try {
    data = await response.json();
  } catch {
    data = null;
  }
  return { ok: response.ok, status: response.status, data };
}

async function saveServer(url) {
  const previous = state.serverUrl;
  state.serverUrl = normalizeServer(url);
  if (previous !== state.serverUrl) {
    state.token = "";
    await chrome.storage.local.set({ [SERVER_KEY]: state.serverUrl, [TOKEN_KEY]: "" });
  } else {
    await chrome.storage.local.set({ [SERVER_KEY]: state.serverUrl });
  }
  $("server-url").value = state.serverUrl;
  $("open-web").href = state.serverUrl;
}

async function testConnection() {
  try {
    const result = await api("/health");
    if (result.ok) {
      setStatus(`连接正常 · ${result.data.version || ""}`, true);
      return true;
    }
    setStatus(`服务端返回 ${result.status}`, false);
  } catch {
    setStatus("无法连接，请确认服务已启动", false);
  }
  return false;
}

async function loadConfig() {
  try {
    const result = await api("/config");
    if (result.ok && result.data) {
      state.captchaEnabled = !!result.data.captchaEnabled;
    }
  } catch {
    state.captchaEnabled = false;
  }
  $("captcha-field").hidden = !state.captchaEnabled;
  if (state.captchaEnabled) {
    await refreshCaptcha();
  }
}

async function refreshCaptcha() {
  try {
    const result = await api("/auth/captcha");
    if (result.ok && result.data) {
      state.captcha = result.data;
      $("captcha-image").innerHTML = `<img src="${result.data.image}" alt="验证码" />`;
    }
  } catch {
    state.captcha = null;
  }
}

function showLoggedIn(user) {
  $("account-info").hidden = false;
  $("account-info").textContent = `已登录：${user.displayName || user.username} · ${user.tenantName || ""}`;
  $("login-form").hidden = true;
  $("logout").hidden = false;
}

function showLoggedOut() {
  $("account-info").hidden = true;
  $("login-form").hidden = false;
  $("logout").hidden = true;
}

async function checkSession() {
  if (!state.token) {
    showLoggedOut();
    return;
  }
  try {
    const result = await api("/auth/me");
    if (result.ok && result.data) {
      showLoggedIn(result.data);
      return;
    }
  } catch {
    // 下方按未登录处理。
  }
  state.token = "";
  await chrome.storage.local.set({ [TOKEN_KEY]: "" });
  showLoggedOut();
}

async function handleLogin(event) {
  event.preventDefault();
  showError("");
  const button = $("login-submit");
  button.disabled = true;
  try {
    const payload = {
      username: $("login-username").value.trim(),
      password: $("login-password").value,
      captchaId: state.captcha ? state.captcha.captchaId : undefined,
      captchaCode: $("login-captcha").value.trim() || undefined,
    };
    const result = await api("/auth/login", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(payload),
    });
    if (!result.ok) {
      showError((result.data && result.data.error) || "登录失败");
      if (state.captchaEnabled) await refreshCaptcha();
      return;
    }
    state.token = result.data.token;
    await chrome.storage.local.set({ [TOKEN_KEY]: state.token });
    showLoggedIn(result.data.user);
    setStatus("已登录，可以开始快速录入", true);
  } catch {
    showError("无法连接服务端");
  } finally {
    button.disabled = false;
  }
}

async function init() {
  const stored = await chrome.storage.local.get([SERVER_KEY, TOKEN_KEY]);
  state.serverUrl = normalizeServer(stored[SERVER_KEY]);
  state.token = stored[TOKEN_KEY] || "";
  $("server-url").value = state.serverUrl;
  $("open-web").href = state.serverUrl;

  await testConnection();
  await loadConfig();
  await checkSession();
}

$("server-form").addEventListener("submit", async (event) => {
  event.preventDefault();
  await saveServer($("server-url").value);
  const ok = await testConnection();
  if (ok) {
    await loadConfig();
    await checkSession();
  }
});
$("login-form").addEventListener("submit", handleLogin);
$("captcha-image").addEventListener("click", () => void refreshCaptcha());
$("logout").addEventListener("click", async () => {
  try {
    await api("/auth/logout", { method: "POST" });
  } catch {
    // 忽略。
  }
  state.token = "";
  await chrome.storage.local.set({ [TOKEN_KEY]: "" });
  showLoggedOut();
  setStatus("已退出登录", true);
});

void init();
