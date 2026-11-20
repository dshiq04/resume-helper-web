const DEFAULT_SERVER = "http://127.0.0.1:18080";
const TOKEN_KEY = "jobtrack.token";
const SERVER_KEY = "jobtrack.serverUrl";
const CATEGORY_KEY = "jobtrack.defaultCategory";

const state = {
  serverUrl: DEFAULT_SERVER,
  token: "",
  captchaEnabled: false,
  captcha: null,
  defaults: null,
  user: null,
};

const $ = (id) => document.getElementById(id);

function normalizeServer(value) {
  return (value || DEFAULT_SERVER).trim().replace(/\/+$/, "") || DEFAULT_SERVER;
}

function showView(name) {
  $("login-view").hidden = name !== "login";
  $("entry-view").hidden = name !== "entry";
}

function showError(element, message) {
  if (!message) {
    element.hidden = true;
    return;
  }
  element.textContent = message;
  element.hidden = false;
}

function setToast(message, isError = false) {
  const toast = $("toast");
  toast.textContent = message;
  toast.classList.toggle("is-error", isError);
  toast.hidden = false;
  window.clearTimeout(setToast.timer);
  setToast.timer = window.setTimeout(() => {
    toast.hidden = true;
  }, 2600);
}

async function loadSettings() {
  const stored = await chrome.storage.local.get([TOKEN_KEY, SERVER_KEY, CATEGORY_KEY]);
  state.serverUrl = normalizeServer(stored[SERVER_KEY]);
  state.token = stored[TOKEN_KEY] || "";
  return stored;
}

async function setToken(token) {
  state.token = token || "";
  await chrome.storage.local.set({ [TOKEN_KEY]: state.token });
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

async function loadPublicConfig() {
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
      const button = $("captcha-image");
      button.innerHTML = `<img src="${result.data.image}" alt="验证码" />`;
    }
  } catch {
    state.captcha = null;
  }
}

async function loadDefaults() {
  try {
    const result = await api("/system/quick-entry/defaults");
    if (result.ok && result.data) {
      state.defaults = result.data;
      $("category").value = result.data.defaultCategory || "spring";
      $("year").value = new Date(result.data.serverTime).getFullYear();
    }
  } catch {
    $("year").value = new Date().getFullYear();
  }
}

function guessCompany(title) {
  if (!title) return "";
  const cleaned = title
    .replace(/招聘|校园招聘|社会招聘|职位详情|岗位详情|官网/gi, " ")
    .split(/[|｜\-–—·•_]/)
    .map((part) => part.trim())
    .filter(Boolean)[0];
  if (!cleaned) return "";
  return cleaned.length > 40 ? cleaned.slice(0, 40) : cleaned;
}

function fillFromQuery() {
  const params = new URLSearchParams(window.location.search);
  const link = params.get("link");
  const title = params.get("title");
  if (link) $("link").value = link;
  if (title) $("company").value = guessCompany(title);
}

async function checkSession() {
  if (!state.token) return false;
  try {
    const result = await api("/auth/me");
    if (result.ok && result.data) {
      state.user = result.data;
      $("account-name").textContent = `${result.data.displayName || result.data.username} · ${result.data.tenantName || ""}`;
      return true;
    }
    if (result.status === 401) {
      await setToken("");
    }
  } catch {
    // 服务端未启动时停留在登录页，用户可以在设置里修改地址。
  }
  return false;
}

async function prefillActiveTab() {
  if ($("link").value) return;
  try {
    const [tab] = await chrome.tabs.query({ active: true, currentWindow: true });
    if (tab && tab.url && /^https?:/i.test(tab.url)) {
      $("link").value = tab.url;
      if (!$("company").value) $("company").value = guessCompany(tab.title || "");
    }
  } catch {
    // 忽略：在独立窗口中无法读取当前标签页。
  }
}

async function suggestCompanies(keyword) {
  try {
    const query = keyword ? `?search=${encodeURIComponent(keyword)}` : "";
    const result = await api(`/applications/companies${query}`);
    if (result.ok && result.data) {
      const list = $("company-options");
      list.innerHTML = "";
      for (const name of result.data.items) {
        const option = document.createElement("option");
        option.value = name;
        list.appendChild(option);
      }
    }
  } catch {
    // 自动补全失败不影响提交。
  }
}

async function handleLogin(event) {
  event.preventDefault();
  const error = $("login-error");
  showError(error, "");
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
      showError(error, (result.data && result.data.error) || "登录失败");
      if (state.captchaEnabled) await refreshCaptcha();
      return;
    }
    await setToken(result.data.token);
    state.user = result.data.user;
    $("account-name").textContent = `${result.data.user.displayName || result.data.user.username} · ${result.data.user.tenantName || ""}`;
    await startEntry();
  } catch (exception) {
    showError(error, "无法连接服务端，请检查设置中的地址");
  } finally {
    button.disabled = false;
  }
}

async function handleSubmit(event) {
  event.preventDefault();
  const company = $("company").value.trim();
  const link = $("link").value.trim();
  if (!company || !link) {
    setToast("公司名称与投递链接都是必填项", true);
    return;
  }
  const button = $("submit");
  button.disabled = true;
  try {
    const payload = {
      companyName: company,
      link,
      category: $("category").value,
      status: $("status").value,
      year: Number($("year").value) || new Date().getFullYear(),
      position: $("position").value.trim() || undefined,
      channel: $("channel").value.trim() || undefined,
    };
    const result = await api("/applications", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(payload),
    });
    if (!result.ok) {
      if (result.status === 401) {
        await setToken("");
        showView("login");
        setToast("登录已失效，请重新登录", true);
      } else {
        setToast((result.data && result.data.error) || "提交失败", true);
      }
      return;
    }
    setToast("已加入投递台账");
    $("company").value = "";
    $("position").value = "";
    $("link").value = "";
    $("company").focus();
    await prefillActiveTab();
  } catch {
    setToast("无法连接服务端", true);
  } finally {
    button.disabled = false;
  }
}

async function startEntry() {
  showView("entry");
  await loadDefaults();
  await prefillActiveTab();
  await suggestCompanies("");
  $("company").focus();
}

async function init() {
  await loadSettings();
  $("server-label").textContent = state.serverUrl.replace(/^https?:\/\//, "");
  $("open-web").href = state.serverUrl;
  $("web-link").href = state.serverUrl;
  fillFromQuery();

  const session = await checkSession();
  if (session) {
    await startEntry();
    return;
  }

  showView("login");
  await loadPublicConfig();
  await prefillActiveTab();
  $("login-username").focus();
}

$("login-form").addEventListener("submit", handleLogin);
$("entry-form").addEventListener("submit", handleSubmit);
$("captcha-image").addEventListener("click", () => void refreshCaptcha());
$("open-options").addEventListener("click", () => chrome.runtime.openOptionsPage());
$("logout").addEventListener("click", async () => {
  try {
    await api("/auth/logout", { method: "POST" });
  } catch {
    // token 本地清除即可。
  }
  await setToken("");
  state.user = null;
  showView("login");
  await loadPublicConfig();
});
$("company").addEventListener("input", (event) => {
  window.clearTimeout(suggestCompanies.timer);
  suggestCompanies.timer = window.setTimeout(() => void suggestCompanies(event.target.value), 250);
});

void init();
