import { useCallback, useEffect, useState } from "react";
import { App as AntdApp, Button, Form, Input, Select } from "antd";
import {
  ArrowRightOutlined,
  MoonOutlined,
  ReloadOutlined,
  SunOutlined,
} from "@ant-design/icons";
import { authApi } from "../api/auth";
import { configApi } from "../api/config";
import type { CaptchaResponse, PublicConfig } from "../api/types";
import { useAuth } from "../context/AuthContext";
import { LANGUAGES, useI18n } from "../i18n";
import { useAppDispatch, useAppSelector } from "../store/hooks";
import { toggleTheme } from "../store/slices/uiSlice";
import { errorMessage } from "../utils/errors";

type Mode = "login" | "register";

export function LoginPage() {
  const { t, lang, setLang } = useI18n();
  const { message } = AntdApp.useApp();
  const { login, register } = useAuth();
  const dispatch = useAppDispatch();
  const themeMode = useAppSelector((state) => state.ui.themeMode);
  const [mode, setMode] = useState<Mode>("login");
  const [config, setConfig] = useState<PublicConfig | null>(null);
  const [captcha, setCaptcha] = useState<CaptchaResponse | null>(null);
  const [submitting, setSubmitting] = useState(false);
  const [form] = Form.useForm();

  const loadCaptcha = useCallback(async () => {
    try {
      setCaptcha(await authApi.captcha());
    } catch {
      setCaptcha(null);
    }
  }, []);

  useEffect(() => {
    void (async () => {
      try {
        const value = await configApi.get();
        setConfig(value);
      } catch {
        setConfig(null);
      }
    })();
  }, []);

  useEffect(() => {
    if (config?.captchaEnabled) {
      void loadCaptcha();
    } else {
      setCaptcha(null);
    }
  }, [config?.captchaEnabled, loadCaptcha]);

  useEffect(() => {
    form.resetFields();
  }, [form, mode]);

  const handleSubmit = async (values: Record<string, string>) => {
    if (config?.captchaEnabled && !values.captchaCode) {
      message.warning(t("auth.captchaPlaceholder"));
      return;
    }
    setSubmitting(true);
    try {
      if (mode === "login") {
        await login({
          username: values.username,
          password: values.password,
          captchaId: captcha?.captchaId,
          captchaCode: values.captchaCode,
        });
      } else {
        await register({
          username: values.username,
          password: values.password,
          displayName: values.displayName,
          tenantName: values.tenantName,
          captchaId: captcha?.captchaId,
          captchaCode: values.captchaCode,
        });
      }
    } catch (error) {
      message.error(errorMessage(error));
      form.setFieldValue("captchaCode", "");
      if (config?.captchaEnabled) {
        void loadCaptcha();
      }
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div className="login-page">
      <section className="login-editorial">
        <div className="login-brand">
          <img className="brand-logo is-large" src="/logo.svg" alt="ResumeTrail" />
          <div>
            <strong>{t("app.name")}</strong>
            <span>RESUMETRAIL</span>
          </div>
        </div>
        <div className="editorial-body">
          <span className="eyebrow">A LEDGER FOR YOUR SEARCH</span>
          <h1>{t("app.tagline")}</h1>
          <p>{t("app.desc")}</p>
          <ul className="editorial-list">
            <li>多用户工作区，账号之间数据完全隔离</li>
            <li>春招、秋招、实习与社招，按年度归档</li>
            <li>状态时间线、简历库、Excel 导入与统计复盘</li>
            <li>Redis 可选的列表缓存与在线状态</li>
          </ul>
        </div>
        <div className="editorial-foot">
          <span>自托管 · SQLite / MySQL / PostgreSQL</span>
          <span>v{config?.version ?? import.meta.env.VITE_APP_VERSION ?? "1.2"}</span>
        </div>
      </section>

      <section className="login-panel">
        <div className="login-toolbar">
          <Select
            size="small"
            className="lang-select"
            value={lang}
            onChange={(value) => setLang(value)}
            options={LANGUAGES}
          />
          <button
            type="button"
            className="ghost-icon"
            onClick={() => dispatch(toggleTheme())}
            title={themeMode === "light" ? t("settings.dark") : t("settings.light")}
          >
            {themeMode === "light" ? <MoonOutlined /> : <SunOutlined />}
          </button>
        </div>

        <div className="login-form-wrap">
          <div className="login-heading">
            <span className="eyebrow">
              {mode === "login" ? "SIGN IN" : "CREATE ACCOUNT"}
            </span>
            <h2>{mode === "login" ? t("auth.loginTitle") : t("auth.registerTitle")}</h2>
            <p>{mode === "login" ? t("auth.loginSubtitle") : t("auth.registerSubtitle")}</p>
          </div>

          <div className="auth-switch">
            <button
              type="button"
              className={mode === "login" ? "is-active" : ""}
              onClick={() => setMode("login")}
            >
              {t("auth.login")}
            </button>
            <button
              type="button"
              className={mode === "register" ? "is-active" : ""}
              onClick={() => setMode("register")}
            >
              {t("auth.register")}
            </button>
          </div>

          <Form form={form} layout="vertical" onFinish={handleSubmit} requiredMark={false}>
            <Form.Item
              name="username"
              label={t("auth.username")}
              rules={[{ required: true, message: t("auth.username") }]}
            >
              <Input size="large" autoComplete="username" placeholder="admin" />
            </Form.Item>
            <Form.Item
              name="password"
              label={t("auth.password")}
              rules={[{ required: true, message: t("auth.password") }]}
            >
              <Input.Password
                size="large"
                autoComplete={mode === "login" ? "current-password" : "new-password"}
                placeholder="••••••"
              />
            </Form.Item>

            {mode === "register" && (
              <>
                <Form.Item name="displayName" label={t("auth.displayName")}>
                  <Input size="large" placeholder={t("auth.displayName")} />
                </Form.Item>
                <Form.Item name="tenantName" label={t("auth.workspace")} extra={t("auth.workspaceHint")}>
                  <Input size="large" placeholder={t("auth.workspace")} />
                </Form.Item>
              </>
            )}

            {config?.captchaEnabled && (
              <Form.Item
                name="captchaCode"
                label={t("auth.captcha")}
                rules={[{ required: true, message: t("auth.captchaPlaceholder") }]}
              >
                <div className="captcha-row">
                  <Input size="large" placeholder={t("auth.captchaPlaceholder")} maxLength={8} />
                  <button
                    type="button"
                    className="captcha-image"
                    onClick={() => void loadCaptcha()}
                    title={t("auth.captchaRefresh")}
                  >
                    {captcha ? (
                      <img src={captcha.image} alt={t("auth.captcha")} />
                    ) : (
                      <ReloadOutlined />
                    )}
                  </button>
                </div>
              </Form.Item>
            )}

            <Button
              type="primary"
              htmlType="submit"
              size="large"
              block
              loading={submitting}
              className="login-submit"
            >
              {mode === "login" ? t("auth.submitLogin") : t("auth.submitRegister")}
              <ArrowRightOutlined />
            </Button>
          </Form>

          {mode === "login" && <p className="login-hint">{t("auth.defaultHint")}</p>}
          {mode === "register" && <p className="login-hint">{t("auth.workspaceHint")}</p>}
        </div>
      </section>
    </div>
  );
}
