import { useCallback, useEffect, useState } from "react";
import {
  App as AntdApp,
  Button,
  Form,
  Input,
  Segmented,
  Select,
  Steps,
  Tag,
} from "antd";
import {
  CheckCircleFilled,
  CloseCircleFilled,
  CloseOutlined,
  DownloadOutlined,
  ReloadOutlined,
} from "@ant-design/icons";
import ReactMarkdown from "react-markdown";
import remarkGfm from "remark-gfm";
import { Link, useNavigate } from "react-router-dom";
import { accountApi } from "../api/account";
import { authApi } from "../api/auth";
import { configApi } from "../api/config";
import { importApi } from "../api/import";
import type { CaptchaResponse, CancelStatus, PublicConfig } from "../api/types";
import { CANCEL_AGREEMENTS } from "../data/cancelAgreement";
import { useAuth } from "../context/AuthContext";
import { useI18n } from "../i18n";
import { COUNTRY_CODES, joinPhone } from "../utils/countryCodes";
import { errorMessage } from "../utils/errors";
import { EMAIL_PATTERN, FULL_PHONE_PATTERN, LOCAL_PHONE_PATTERN } from "../utils/validation";

type ContactMethod = "phone" | "email";

interface VerifyValues {
  contactType: ContactMethod;
  countryCode: string;
  phone: string;
  email: string;
  password: string;
}

interface ConfirmValues {
  captchaCode?: string;
}

/**
 * 用户注销页：注销协议 → 条件检查 → 身份验证 → 协议确认与验证码。
 */
export function AccountCancelPage() {
  const { t, lang } = useI18n();
  const { message } = AntdApp.useApp();
  const { clearSession } = useAuth();
  const navigate = useNavigate();
  const [step, setStep] = useState(0);
  const [status, setStatus] = useState<CancelStatus | null>(null);
  const [checking, setChecking] = useState(false);
  const [verifying, setVerifying] = useState(false);
  const [confirming, setConfirming] = useState(false);
  const [config, setConfig] = useState<PublicConfig | null>(null);
  const [captcha, setCaptcha] = useState<CaptchaResponse | null>(null);
  const [exporting, setExporting] = useState(false);
  const [verifyForm] = Form.useForm<VerifyValues>();
  const [confirmForm] = Form.useForm<ConfirmValues>();
  const contactType = Form.useWatch("contactType", verifyForm) ?? "phone";

  const loadCaptcha = useCallback(async () => {
    try {
      setCaptcha(await authApi.captcha());
    } catch {
      setCaptcha(null);
    }
  }, []);

  useEffect(() => {
    void configApi.get().then((value) => {
      setConfig(value);
      if (value?.captchaEnabled) {
        void loadCaptcha();
      }
    });
  }, [loadCaptcha]);

  const checkStatus = useCallback(async () => {
    setChecking(true);
    try {
      setStatus(await accountApi.cancelStatus());
    } catch (error) {
      message.error(errorMessage(error));
    } finally {
      setChecking(false);
    }
  }, [message]);

  useEffect(() => {
    if (step === 1) {
      void checkStatus();
    }
  }, [step, checkStatus]);

  const exportApplications = async () => {
    setExporting(true);
    try {
      await importApi.exportAll();
      message.success(t("account.cancelExported"));
      void checkStatus();
    } catch (error) {
      message.error(errorMessage(error));
    } finally {
      setExporting(false);
    }
  };

  const conditionsPass = !!status
    && status.applications === 0
    && status.trash === 0
    && status.resumes === 0
    && !status.elevatedPermission;

  const verify = async (values: VerifyValues) => {
    const isPhone = values.contactType !== "email";
    const contact = isPhone
      ? joinPhone(values.countryCode, values.phone)
      : values.email.trim();
    if (isPhone && !FULL_PHONE_PATTERN.test(contact)) {
      message.error(t("settings.phoneInvalid"));
      return;
    }
    if (!isPhone && !EMAIL_PATTERN.test(contact)) {
      message.error(t("settings.emailInvalid"));
      return;
    }
    setVerifying(true);
    try {
      await accountApi.cancelVerify({
        contactType: isPhone ? "phone" : "email",
        contact,
        password: values.password,
      });
      setStep(3);
      if (config?.captchaEnabled) {
        void loadCaptcha();
      }
    } catch (error) {
      message.error(errorMessage(error));
    } finally {
      setVerifying(false);
    }
  };

  const confirm = async (values: ConfirmValues) => {
    const data = verifyForm.getFieldsValue();
    const isPhone = data.contactType !== "email";
    const contact = isPhone
      ? joinPhone(data.countryCode, data.phone)
      : data.email.trim();
    if (config?.captchaEnabled && (!captcha || !values.captchaCode)) {
      message.warning(t("auth.captchaPlaceholder"));
      return;
    }
    setConfirming(true);
    try {
      await accountApi.cancelConfirm({
        captchaId: captcha?.captchaId,
        captchaCode: values.captchaCode ?? "",
        contactType: isPhone ? "phone" : "email",
        contact,
        password: data.password,
      });
      // 注销成功：服务端已删除 Redis 中的令牌与权限，这里清理本地状态并回到登录页。
      message.success(t("account.cancelSuccess"));
      clearSession();
      navigate("/login", { replace: true });
    } catch (error) {
      message.error(errorMessage(error));
      if (config?.captchaEnabled) {
        void loadCaptcha();
      }
      confirmForm.setFieldValue("captchaCode", "");
    } finally {
      setConfirming(false);
    }
  };

  const conditionRow = (pass: boolean, label: string) => (
    <div className="cancel-condition" key={label}>
      {pass ? (
        <CheckCircleFilled className="is-pass" />
      ) : (
        <CloseCircleFilled className="is-fail" />
      )}
      <span>{label}</span>
      {pass && <Tag color="green">{t("account.cancelConditionPass")}</Tag>}
    </div>
  );

  return (
    <div className="account-layout cancel-layout">
      <section className="setting-card account-cancel-card">
        <div className="setting-card-head">
          <h2>{t("account.cancelTitle")}</h2>
        </div>
        <Steps
          size="small"
          current={step}
          items={[
            { title: t("account.cancelStepAgreement") },
            { title: t("account.cancelStepCheck") },
            { title: t("account.cancelStepVerify") },
            { title: t("account.cancelStepConfirm") },
          ]}
        />

        {step === 0 && (
          <div className="cancel-agreement-step">
            <div className="agreement-body cancel-agreement-body">
              <ReactMarkdown remarkPlugins={[remarkGfm]}>
                {CANCEL_AGREEMENTS[lang]}
              </ReactMarkdown>
            </div>
            <div className="cancel-actions">
              <Button icon={<CloseOutlined />} onClick={() => navigate("/account/tokens")}>
                {t("account.cancelClose")}
              </Button>
              <Button type="primary" danger onClick={() => setStep(1)}>
                {t("account.cancelNext")}
              </Button>
            </div>
          </div>
        )}

        {step === 1 && (
          <div className="cancel-agreement-step">
            <div className="cancel-conditions">
              {conditionRow(
                !!status && status.applications === 0 && status.trash === 0,
                t("account.cancelCondApplications", {
                  count: status ? status.applications + status.trash : 0,
                }),
              )}
              {(!status || status.applications > 0 || status.trash > 0) && (
                <div className="cancel-condition-actions">
                  <Button
                    icon={<ReloadOutlined />}
                    loading={checking}
                    onClick={() => void checkStatus()}
                  >
                    {t("account.cancelRefresh")}
                  </Button>
                  <Link to="/">
                    <Button>{t("account.cancelGoApplications")}</Button>
                  </Link>
                  <Button
                    icon={<DownloadOutlined />}
                    loading={exporting}
                    onClick={() => void exportApplications()}
                  >
                    {t("account.cancelExport")}
                  </Button>
                </div>
              )}
              {conditionRow(
                !!status && status.resumes === 0,
                t("account.cancelCondResumes", { count: status?.resumes ?? 0 }),
              )}
              {(!status || status.resumes > 0) && (
                <div className="cancel-condition-actions">
                  <Link to="/resumes">
                    <Button>{t("account.cancelGoResumes")}</Button>
                  </Link>
                </div>
              )}
              {conditionRow(
                !!status && !status.elevatedPermission,
                t("account.cancelCondPermission"),
              )}
              {(!status || status.elevatedPermission) && (
                <p className="setting-hint">{t("account.cancelPermissionHint")}</p>
              )}
            </div>
            <div className="cancel-actions">
              <Button onClick={() => setStep(0)}>{t("common.back")}</Button>
              <Button
                type="primary"
                danger
                disabled={!conditionsPass}
                loading={checking}
                onClick={() => setStep(2)}
              >
                {t("account.cancelNext")}
              </Button>
            </div>
          </div>
        )}

        {step === 2 && (
          <div className="cancel-agreement-step">
            <p className="cancel-lead">{t("account.cancelVerifyLead")}</p>
            <Form
              form={verifyForm}
              layout="vertical"
              onFinish={verify}
              onFinishFailed={() => message.warning(t("settings.fixErrors"))}
              requiredMark={false}
            >
              <Form.Item name="contactType" label={t("settings.contactMethod")} initialValue="phone">
                <Segmented
                  options={[
                    { value: "phone", label: t("settings.contactPhone") },
                    { value: "email", label: t("settings.contactEmail") },
                  ]}
                />
              </Form.Item>
              {contactType === "phone" ? (
                <div className="form-grid two">
                  <Form.Item name="countryCode" label={t("auth.countryCode")} initialValue="+86">
                    <Select showSearch optionFilterProp="label" options={COUNTRY_CODES} />
                  </Form.Item>
                  <Form.Item
                    name="phone"
                    label={t("auth.phone")}
                    rules={[{ required: true, message: t("settings.phoneRequired") }]}
                  >
                    <Input inputMode="tel" maxLength={15} placeholder="13800138000" />
                  </Form.Item>
                </div>
              ) : (
                <Form.Item
                  name="email"
                  label={t("auth.email")}
                  rules={[{ required: true, message: t("settings.emailRequired") }]}
                >
                  <Input autoComplete="email" maxLength={128} />
                </Form.Item>
              )}
              <Form.Item
                name="password"
                label={t("auth.password")}
                rules={[{ required: true, message: t("auth.password") }]}
              >
                <Input.Password autoComplete="current-password" />
              </Form.Item>
              <div className="cancel-actions">
                <Button onClick={() => setStep(1)}>{t("common.back")}</Button>
                <Button type="primary" danger htmlType="submit" loading={verifying}>
                  {t("account.cancelNext")}
                </Button>
              </div>
            </Form>
          </div>
        )}

        {step === 3 && (
          <div className="cancel-agreement-step">
            <div className="agreement-body cancel-agreement-body">
              <ReactMarkdown remarkPlugins={[remarkGfm]}>
                {CANCEL_AGREEMENTS[lang]}
              </ReactMarkdown>
            </div>
            <Form
              form={confirmForm}
              layout="vertical"
              onFinish={confirm}
              requiredMark={false}
            >
              {config?.captchaEnabled && (
                <Form.Item label={t("auth.captcha")} required>
                  <div className="captcha-row">
                    <Form.Item
                      name="captchaCode"
                      noStyle
                      rules={[{ required: true, message: t("auth.captchaPlaceholder") }]}
                    >
                      <Input size="large" placeholder={t("auth.captchaPlaceholder")} maxLength={8} />
                    </Form.Item>
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
            </Form>
            <div className="cancel-actions">
              <Button onClick={() => setStep(2)}>{t("common.back")}</Button>
              <Button
                type="primary"
                danger
                loading={confirming}
                onClick={() => confirmForm.submit()}
              >
                {t("account.cancelConfirmButton")}
              </Button>
            </div>
          </div>
        )}
      </section>
    </div>
  );
}
