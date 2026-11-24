const DEFAULT_HOST = "http://127.0.0.1";
const DEFAULT_PORT = "18080";
const SERVER_KEY = "jobtrack.serverUrl";
const CODE_KEY = "jobtrack.accessCode";
const CATEGORY_KEY = "jobtrack.defaultCategory";

const state = {
  serverUrl: `${DEFAULT_HOST}:${DEFAULT_PORT}`,
  accessCode: "",
  info: null,
  defaults: null,
  category: "spring",
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

function showView(name) {
  $("connect-view").hidden = name !== "connect";
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
  const stored = await chrome.storage.local.get([SERVER_KEY, CODE_KEY, CATEGORY_KEY]);
  state.serverUrl = stored[SERVER_KEY] || state.serverUrl;
  state.accessCode = stored[CODE_KEY] || "";
  const { host, port } = parseServer(state.serverUrl);
  $("server-host").value = host;
  $("server-port").value = port;
  $("access-code").value = state.accessCode;
  return stored;
}

async function api(path, options = {}) {
  const headers = { Accept: "application/json", ...(options.headers || {}) };
  if (state.accessCode) {
    headers.Authorization = `Bearer ${state.accessCode}`;
  }
  const response = await fetch(`${state.serverUrl}/api${path}`, { ...options, headers });
  let payload = null;
  try {
    payload = await response.json();
  } catch {
    payload = null;
  }
  const envelope = payload && typeof payload.code === "number" ? payload : null;
  return {
    ok: response.ok,
    status: response.status,
    code: envelope ? envelope.code : response.status,
    message: envelope ? envelope.message : null,
    data: envelope ? (envelope.data ?? null) : payload,
  };
}

async function validateCode(code) {
  const response = await fetch(`${state.serverUrl}/api/tokens/validate`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ code }),
  });
  let payload = null;
  try {
    payload = await response.json();
  } catch {
    payload = null;
  }
  const envelope = payload && typeof payload.code === "number" ? payload : null;
  return {
    status: response.status,
    code: envelope ? envelope.code : response.status,
    message: envelope ? envelope.message : null,
    data: envelope ? (envelope.data ?? null) : payload,
  };
}

function validateMessage(result) {
  if (result.message) {
    return result.message;
  }
  if (result.data && result.data.reason === "banned") {
    return result.data.message || "该账号已被封禁";
  }
  if (result.status === 410 || (result.data && result.data.reason === "expired")) {
    return "授权码已过期，请重新生成";
  }
  if (result.status === 403 || (result.data && result.data.reason === "disabled")) {
    return "授权码已停用或账号状态异常";
  }
  if (result.status === 401 || (result.data && result.data.reason === "invalid")) {
    return "授权码不存在或已被删除";
  }
  if (result.status === 400) {
    return "请输入正确的授权码";
  }
  return "授权码校验失败，请稍后重试";
}

function scopeLabel(scope) {
  if (scope === "read") return "只读";
  if (scope === "write") return "只写";
  return "读写";
}

function applyScope() {
  const scope = state.info ? state.info.scope : "";
  const readOnly = scope === "read";
  $("account-scope").hidden = !scope;
  $("account-scope").textContent = scopeLabel(scope);
  $("read-only-note").hidden = !readOnly;
  $("submit").disabled = readOnly;
}

function applyCategory() {
  for (const button of document.querySelectorAll("#category-pills button")) {
    button.classList.toggle("is-active", button.dataset.value === state.category);
  }
}

function selectCategory(value) {
  state.category = value || "spring";
  applyCategory();
}

async function loadDefaults() {
  try {
    const result = await api("/system/quick-entry/defaults");
    if (result.ok && result.data) {
      state.defaults = result.data;
      selectCategory(result.data.defaultCategory);
    }
  } catch {
    applyCategory();
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

function isAppUrl(url) {
  if (!url) return false;
  try {
    const parsed = new URL(url);
    if ((parsed.hostname === "127.0.0.1" || parsed.hostname === "localhost")
        && parsed.port === "5173") {
      return true;
    }
    return parsed.origin === new URL(state.serverUrl).origin;
  } catch {
    return false;
  }
}

function fillFromQuery() {
  const params = new URLSearchParams(window.location.search);
  const link = params.get("link");
  const title = params.get("title");
  if (link && !isAppUrl(link)) {
    $("link").value = link;
  }
  if (title && !isAppUrl(link)) {
    $("company").value = guessCompany(title);
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

async function startEntry() {
  const info = state.info || {};
  $("account-name").textContent = info.displayName
    ? `${info.displayName} · @${info.username}`
    : info.username || "已连接";
  applyScope();
  showView("entry");
  await loadDefaults();
  await suggestCompanies("");
  const focusTarget = state.info && state.info.scope === "read" ? $("link") : $("company");
  focusTarget.focus();
}

async function handleConnect(event) {
  event.preventDefault();
  const error = $("connect-error");
  showError(error, "");
  const button = $("connect-submit");
  button.disabled = true;
  try {
    state.serverUrl = normalizeServer($("server-host").value, $("server-port").value);
    const code = $("access-code").value.trim();
    if (!code) {
      showError(error, "请填写授权码");
      return;
    }
    const result = await validateCode(code);
    if (!result.data || !result.data.valid) {
      showError(error, validateMessage(result));
      return;
    }
    if (result.data.scope !== "read" && result.data.scope !== "readwrite") {
      showError(error, "该授权码为只写权限，无法读取数据；请使用读或读写权限");
      return;
    }
    state.accessCode = code;
    state.info = result.data;
    await chrome.storage.local.set({
      [SERVER_KEY]: state.serverUrl,
      [CODE_KEY]: state.accessCode,
    });
    await startEntry();
  } catch {
    showError(error, "无法连接服务端，请检查服务器与端口");
  } finally {
    button.disabled = false;
  }
}

async function handleSubmit(event) {
  event.preventDefault();
  if (state.info && state.info.scope === "read") {
    setToast("只读授权码无法录入，请更换读写授权码", true);
    return;
  }
  const company = $("company").value.trim();
  const link = $("link").value.trim();
  if (!company || !link) {
    setToast("公司名称与投递链接都是必填项", true);
    return;
  }
  const button = $("submit");
  button.disabled = true;
  try {
    // 每次录入前重新校验：存在、未过期、用户可用、状态正常。
    const checked = await validateCode(state.accessCode);
    if (!checked.data || !checked.data.valid) {
      setToast(validateMessage(checked), true);
      showConnectView(validateMessage(checked));
      return;
    }
    if (checked.data.scope === "read") {
      setToast("只读授权码无法录入，请更换读写授权码", true);
      return;
    }
    state.info = checked.data;
    applyScope();
    const payload = {
      companyName: company,
      link,
      category: state.category,
      status: "screening",
      year: new Date().getFullYear(),
    };
    const result = await api("/applications", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(payload),
    });
    if (!result.ok) {
      if (result.status === 401) {
        setToast("授权码已失效，请重新校验", true);
        showConnectView("授权码已失效，请重新校验");
      } else if (result.status === 403) {
        setToast(result.message || (result.data && result.data.error) || "授权码权限不足，无法录入", true);
      } else {
        setToast(result.message || (result.data && result.data.error) || "提交失败", true);
      }
      return;
    }
    setToast("已加入投递台账");
    $("company").value = "";
    $("link").value = "";
    $("company").focus();
  } catch {
    setToast("无法连接服务端", true);
  } finally {
    button.disabled = false;
  }
}

function showConnectView(message) {
  showView("connect");
  showError($("connect-error"), message || "");
  $("access-code").focus();
}

async function init() {
  await loadSettings();
  fillFromQuery();

  if (!state.accessCode) {
    showView("connect");
    return;
  }
  try {
    const result = await validateCode(state.accessCode);
    if (result.data && result.data.valid
        && (result.data.scope === "read" || result.data.scope === "readwrite")) {
      state.info = result.data;
      await startEntry();
      return;
    }
    showConnectView(validateMessage(result));
  } catch {
    showConnectView("无法连接服务端，请检查服务器与端口");
  }
}

$("connect-form").addEventListener("submit", handleConnect);
$("entry-form").addEventListener("submit", handleSubmit);
$("open-connect").addEventListener("click", () => showConnectView(""));
$("open-options").addEventListener("click", () => chrome.runtime.openOptionsPage());
$("close-popup").addEventListener("click", () => window.close());
$("clear").addEventListener("click", () => {
  $("company").value = "";
  $("link").value = "";
  $("company").focus();
});
for (const button of document.querySelectorAll("#category-pills button")) {
  button.addEventListener("click", () => selectCategory(button.dataset.value));
}
$("company").addEventListener("input", (event) => {
  window.clearTimeout(suggestCompanies.timer);
  suggestCompanies.timer = window.setTimeout(() => void suggestCompanies(event.target.value), 250);
});

void init();
