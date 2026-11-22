import { useCallback, useEffect, useState } from "react";
import { App as AntdApp, Button, Checkbox, Form, Input, Segmented, Select } from "antd";
import {
  ArrowRightOutlined,
  MoonOutlined,
  ReloadOutlined,
  SunOutlined,
} from "@ant-design/icons";
import { authApi } from "../api/auth";
import { configApi } from "../api/config";
import type { CaptchaResponse, PublicConfig } from "../api/types";
import { UserAgreementModal } from "../components/UserAgreementModal";
import { useAuth } from "../context/AuthContext";
import { LANGUAGES, useI18n } from "../i18n";
import { useAppDispatch, useAppSelector } from "../store/hooks";
import { toggleTheme } from "../store/slices/uiSlice";
import { COUNTRY_CODES, joinPhone } from "../utils/countryCodes";
import { errorMessage, isApiErrorCode } from "../utils/errors";
import { EMAIL_PATTERN, FULL_PHONE_PATTERN, LOCAL_PHONE_PATTERN } from "../utils/validation";

type Mode = "login" | "register" | "forgot";

interface AuthValues {
  username: string;
  password: string;
  confirmPassword?: string;
  displayName?: string;
  tenantName?: string;
  captchaCode?: string;
  contactType?: "phone" | "email";
  countryCode?: string;
  phone?: string;
  email?: string;
  agreement?: boolean;
}

interface ForgotValues {
  username: string;
  contactType: "phone" | "email";
  countryCode: string;
  phone: string;
  email: string;
  newPassword: string;
  confirmPassword: string;
}

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
  const [agreementOpen, setAgreementOpen] = useState(false);
  const [agreementAccepted, setAgreementAccepted] = useState(false);
  const [forgotStep, setForgotStep] = useState<1 | 2>(1);
  const [forgotSubmitting, setForgotSubmitting] = useState(false);
  const [forgotContact, setForgotContact] = useState("");
  const [forgotContactType, setForgotContactType] = useState<"phone" | "email">("phone");
  const [forgotUsername, setForgotUsername] = useState("");
  const [forgotForm] = Form.useForm<ForgotValues>();
  const forgotContactTypeWatch = Form.useWatch("contactType", forgotForm) ?? "phone";
  const [form] = Form.useForm<AuthValues>();
  const contactType = Form.useWatch("contactType", form) ?? "phone";

  const loadCaptcha = useCallback(async () => {
    try {
      setCaptcha(await authApi.captcha());
    } catch {
      setCaptcha(null);
    }
  }, []);

  const loadConfig = useCallback(async () => {
    try {
      const value = await configApi.get();
      setConfig(value);
      return value;
    } catch {
      setConfig(null);
      return null;
    }
  }, []);

  useEffect(() => {
    void loadConfig();
  }, [loadConfig]);

  useEffect(() => {
    if (config?.captchaEnabled) {
      void loadCaptcha();
    } else {
      setCaptcha(null);
    }
  }, [config?.captchaEnabled, loadCaptcha]);

  useEffect(() => {
    form.resetFields();
    setAgreementAccepted(false);
  }, [form, mode]);

  const forgotContactValue = (values: ForgotValues) => {
    const isPhone = values.contactType !== "email";
    return isPhone ? joinPhone(values.countryCode ?? "+86", values.phone ?? "") : values.email?.trim() ?? "";
  };

  /** 忘记密码：先验证用户名与手机号/邮箱，验证通过后允许修改密码。 */
  const handleForgot = async (values: ForgotValues) => {
    const isPhone = values.contactType !== "email";
    const contact = forgotContactValue(values);
    if (isPhone && !FULL_PHONE_PATTERN.test(contact)) {
      message.error(t("settings.phoneInvalid"));
      return;
    }
    if (!isPhone && !EMAIL_PATTERN.test(contact)) {
      message.error(t("settings.emailInvalid"));
      return;
    }
    setForgotSubmitting(true);
    try {
      if (forgotStep === 1) {
        await authApi.forgotPasswordVerify({
          username: values.username.trim(),
          contactType: isPhone ? "phone" : "email",
          contact,
        });
        setForgotContact(contact);
        setForgotContactType(isPhone ? "phone" : "email");
        setForgotUsername(values.username.trim());
        setForgotStep(2);
        message.success(t("auth.forgotVerified"));
      } else {
        if (values.newPassword !== values.confirmPassword) {
          message.error(t("settings.passwordMismatch"));
          return;
        }
        await authApi.forgotPasswordReset({
          username: forgotUsername,
          contactType: forgotContactType,
          contact: forgotContact,
          newPassword: values.newPassword,
          confirmPassword: values.confirmPassword ?? "",
        });
        message.success(t("auth.forgotResetDone"));
        // 服务端已注销该用户全部旧令牌，这里回到登录页让用户用新密码登录。
        setForgotStep(1);
        setMode("login");
      }
    } catch (error) {
      message.error(errorMessage(error));
    } finally {
      setForgotSubmitting(false);
    }
  };

  const handleSubmit = async (values: AuthValues) => {
    // 首次进入页面时配置可能还没返回，先等待，避免漏掉验证码字段导致登录失败。
    let currentConfig = config;
    if (!currentConfig) {
      currentConfig = await loadConfig();
    }
    if (!currentConfig) {
      message.error(t("error.network"));
      return;
    }
    if (currentConfig.captchaEnabled && !captcha) {
      message.warning(t("auth.captchaRefresh"));
      void loadCaptcha();
      return;
    }
    if (currentConfig.captchaEnabled && !values.captchaCode) {
      message.warning(t("auth.captchaPlaceholder"));
      return;
    }
    const registerContactType = values.contactType === "email" ? "email" : "phone";
    if (mode === "register") {
      if (values.password !== values.confirmPassword) {
        message.error(t("settings.passwordMismatch"));
        return;
      }
      if (registerContactType === "phone") {
        if (!values.phone?.trim()) {
          message.warning(t("settings.phoneRequired"));
          return;
        }
        if (!FULL_PHONE_PATTERN.test(joinPhone(values.countryCode ?? "+86", values.phone))) {
          message.error(t("settings.phoneInvalid"));
          return;
        }
      } else {
        const email = values.email?.trim() ?? "";
        if (!email) {
          message.warning(t("settings.emailRequired"));
          return;
        }
        if (!EMAIL_PATTERN.test(email)) {
          message.error(t("settings.emailInvalid"));
          return;
        }
      }
    }
    if (!agreementAccepted) {
      message.warning(t("auth.agreementRequired"));
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
          confirmPassword: values.confirmPassword,
          displayName: values.displayName,
          tenantName: values.tenantName,
          contactType: registerContactType,
          countryCode: registerContactType === "phone" ? values.countryCode : undefined,
          phone: registerContactType === "phone" ? values.phone?.trim() : undefined,
          email: registerContactType === "email" ? values.email?.trim() : undefined,
          agreementAccepted: true,
          captchaId: captcha?.captchaId,
          captchaCode: values.captchaCode,
        });
      }
    } catch (error) {
      // 登录失败统一提示，避免显示通用错误或暴露账号是否存在。
      message.error(
        isApiErrorCode(error, "invalid_credentials")
          ? t("auth.invalidCredentials")
          : errorMessage(error),
      );
      form.setFieldValue("captchaCode", "");
      if (currentConfig.captchaEnabled) {
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
          <span className="eyebrow">{t("login.eyebrow")}</span>
          <h1>{t("app.tagline")}</h1>
          <p>{t("app.desc")}</p>
          <ul className="editorial-list">
            <li>{t("login.point1")}</li>
            <li>{t("login.point2")}</li>
            <li>{t("login.point3")}</li>
            <li>{t("login.point4")}</li>
          </ul>
        </div>
        <div className="editorial-foot">
          <span>{t("login.selfHosted")}</span>
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
              {mode === "login" ? t("auth.signInEyebrow") : t("auth.createAccountEyebrow")}
            </span>
            <h2>{mode === "login" ? t("auth.loginTitle") : t("auth.registerTitle")}</h2>
            {mode === "login" && <p>{t("auth.loginSubtitle")}</p>}
          </div>

          <div className="auth-switch">
            <button
              type="button"
              className={mode === "login" ? "is-active" : ""}
              onClick={() => {
                setForgotStep(1);
                setMode("login");
              }}
            >
              {t("auth.login")}
            </button>
            <button
              type="button"
              className={mode === "register" ? "is-active" : ""}
              onClick={() => {
                setForgotStep(1);
                setMode("register");
              }}
            >
              {t("auth.register")}
            </button>
          </div>

          {mode === "forgot" ? (
            <>
              <div className="login-heading">
                <span className="eyebrow">{t("auth.forgotEyebrow")}</span>
                <h2>{t("auth.forgotTitle")}</h2>
                <p>{t("auth.forgotSubtitle")}</p>
              </div>
              <Form
                form={forgotForm}
                layout="vertical"
                onFinish={handleForgot}
                onFinishFailed={() => message.warning(t("settings.fixErrors"))}
                requiredMark={false}
              >
                {forgotStep === 1 ? (
                  <>
                    <Form.Item
                      name="username"
                      label={t("auth.username")}
                      rules={[{ required: true, message: t("auth.username") }]}
                    >
                      <Input size="large" autoComplete="username" placeholder={t("auth.usernamePlaceholder")} />
                    </Form.Item>
                    <Form.Item
                      name="contactType"
                      label={t("settings.contactMethod")}
                      initialValue="phone"
                    >
                      <Segmented
                        options={[
                          { value: "phone", label: t("auth.phone") },
                          { value: "email", label: t("auth.email") },
                        ]}
                      />
                    </Form.Item>
                    {forgotContactTypeWatch === "phone" ? (
                      <div className="form-grid two">
                        <Form.Item
                          name="countryCode"
                          label={t("auth.countryCode")}
                          initialValue="+86"
                        >
                          <Select size="large" showSearch optionFilterProp="label" options={COUNTRY_CODES} />
                        </Form.Item>
                        <Form.Item
                          name="phone"
                          label={t("auth.phone")}
                          rules={[
                            { required: true, message: t("settings.phoneRequired") },
                            { pattern: LOCAL_PHONE_PATTERN, message: t("settings.phoneInvalid") },
                          ]}
                        >
                          <Input size="large" inputMode="tel" maxLength={15} placeholder="13800138000" />
                        </Form.Item>
                      </div>
                    ) : (
                      <Form.Item
                        name="email"
                        label={t("auth.email")}
                        rules={[
                          { required: true, message: t("settings.emailRequired") },
                          { type: "email", message: t("settings.emailInvalid") },
                        ]}
                      >
                        <Input size="large" autoComplete="email" maxLength={128} />
                      </Form.Item>
                    )}
                  </>
                ) : (
                  <>
                    <div className="form-grid two">
                      <Form.Item
                        name="newPassword"
                        label={t("settings.newPassword")}
                        rules={[
                          { required: true, message: t("settings.newPasswordRequired") },
                          { min: 6, max: 64, message: t("settings.newPasswordInvalid") },
                        ]}
                      >
                        <Input.Password size="large" autoComplete="new-password" />
                      </Form.Item>
                      <Form.Item
                        name="confirmPassword"
                        label={t("settings.confirmPassword")}
                        dependencies={["newPassword"]}
                        rules={[
                          { required: true, message: t("settings.confirmPasswordRequired") },
                          ({ getFieldValue }) => ({
                            validator(_, value) {
                              if (!value || value === getFieldValue("newPassword")) {
                                return Promise.resolve();
                              }
                              return Promise.reject(new Error(t("settings.passwordMismatch")));
                            },
                          }),
                        ]}
                      >
                        <Input.Password size="large" autoComplete="new-password" />
                      </Form.Item>
                    </div>
                    <p className="forgot-hint">{t("auth.forgotResetHint")}</p>
                  </>
                )}
                <Button
                  type="primary"
                  htmlType="submit"
                  size="large"
                  block
                  loading={forgotSubmitting}
                  className="login-submit"
                >
                  {forgotStep === 1 ? t("auth.forgotVerify") : t("auth.forgotReset")}
                  <ArrowRightOutlined />
                </Button>
              </Form>
              <button
                type="button"
                className="forgot-back-link"
                onClick={() => {
                  setForgotStep(1);
                  setMode("login");
                }}
              >
                {t("auth.forgotBack")}
              </button>
            </>
          ) : (
          <Form form={form} layout="vertical" onFinish={handleSubmit}>
            <Form.Item
              name="username"
              label={t("auth.username")}
              rules={[
                { required: true, message: t("auth.username") },
                ...(mode === "register"
                  ? [{ pattern: /^[A-Za-z0-9_.-]{3,32}$/, message: t("account.usernameHint") }]
                  : []),
              ]}
            >
              <Input size="large" autoComplete="username" placeholder={t("auth.usernamePlaceholder")} />
            </Form.Item>
            <Form.Item
              name="password"
              label={t("auth.password")}
              rules={[
                { required: true, message: t("auth.password") },
                ...(mode === "register"
                  ? [{ min: 6, max: 64, message: t("settings.newPasswordInvalid") }]
                  : []),
              ]}
            >
              <Input.Password
                size="large"
                autoComplete={mode === "login" ? "current-password" : "new-password"}
                placeholder={t("auth.passwordPlaceholder")}
              />
            </Form.Item>
            {mode === "register" && (
              <Form.Item
                name="confirmPassword"
                label={t("settings.confirmPassword")}
                dependencies={["password"]}
                rules={[
                  { required: true, message: t("settings.confirmPasswordRequired") },
                  ({ getFieldValue }) => ({
                    validator(_, value) {
                      if (!value || value === getFieldValue("password")) {
                        return Promise.resolve();
                      }
                      return Promise.reject(new Error(t("settings.passwordMismatch")));
                    },
                  }),
                ]}
              >
                <Input.Password
                  size="large"
                  autoComplete="new-password"
                  placeholder={t("settings.confirmPassword")}
                />
              </Form.Item>
            )}

            {mode === "register" && (
              <>
                <Form.Item
                  name="contactType"
                  label={t("settings.contactMethod")}
                  initialValue="phone"
                  rules={[{ required: true, message: t("auth.contactRequired") }]}
                >
                  <Segmented
                    options={[
                      { value: "phone", label: t("auth.phone") },
                      { value: "email", label: t("auth.email") },
                    ]}
                  />
                </Form.Item>
                {contactType === "phone" ? (
                  <div className="form-grid two">
                    <Form.Item
                      name="countryCode"
                      label={t("auth.countryCode")}
                      initialValue="+86"
                    >
                      <Select
                        size="large"
                        showSearch
                        optionFilterProp="label"
                        options={COUNTRY_CODES}
                      />
                    </Form.Item>
                    <Form.Item
                      name="phone"
                      label={t("auth.phone")}
                      extra={t("auth.contactHint")}
                      rules={[
                        { required: true, message: t("settings.phoneRequired") },
                        { pattern: LOCAL_PHONE_PATTERN, message: t("settings.phoneInvalid") },
                      ]}
                    >
                      <Input
                        size="large"
                        inputMode="tel"
                        maxLength={15}
                        placeholder="13800138000"
                      />
                    </Form.Item>
                  </div>
                ) : (
                  <Form.Item
                    name="email"
                    label={t("auth.email")}
                    extra={t("auth.contactHint")}
                    rules={[
                      { required: true, message: t("settings.emailRequired") },
                      { type: "email", message: t("settings.emailInvalid") },
                    ]}
                  >
                    <Input
                      size="large"
                      autoComplete="email"
                      maxLength={128}
                      placeholder={t("auth.email")}
                    />
                  </Form.Item>
                )}
                <Form.Item
                  name="displayName"
                  label={t("auth.displayName")}
                  rules={[{ required: true, message: t("auth.displayName") }]}
                >
                  <Input size="large" maxLength={64} placeholder={t("auth.displayName")} />
                </Form.Item>
                <Form.Item
                  name="tenantName"
                  label={t("auth.workspace")}
                  rules={[{ required: true, message: t("auth.workspace") }]}
                >
                  <Input size="large" maxLength={64} placeholder={t("auth.workspace")} />
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

            <div className="agreement-row">
              <Checkbox
                checked={agreementAccepted}
                onChange={() => setAgreementOpen(true)}
              >
                <span className="agreement-required">*</span>
                {t("auth.agreementPrefix")}
                <button
                  type="button"
                  className="agreement-link"
                  onClick={(event) => {
                    event.preventDefault();
                    event.stopPropagation();
                    setAgreementOpen(true);
                  }}
                >
                  {t("auth.agreementLink")}
                </button>
              </Checkbox>
            </div>

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
          )}

          {mode === "login" && (
            <button
              type="button"
              className="forgot-link"
              onClick={() => {
                forgotForm.resetFields();
                setForgotStep(1);
                setMode("forgot");
              }}
            >
              {t("auth.forgotLink")}
            </button>
          )}
        </div>
      </section>

      <UserAgreementModal
        open={agreementOpen}
        onClose={() => setAgreementOpen(false)}
        onAgree={() => setAgreementAccepted(true)}
      />
    </div>
  );
}
