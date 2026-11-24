import { useEffect, useState } from "react";
import {
  Alert,
  App as AntdApp,
  Button,
  Form,
  Input,
  InputNumber,
  Segmented,
  Select,
  Switch,
} from "antd";
import { CopyOutlined, InfoCircleOutlined, LogoutOutlined, UserOutlined } from "@ant-design/icons";
import { authApi } from "../api/auth";
import { databaseApi } from "../api/database";
import type { DatabaseInfo } from "../api/types";
import { PageHeader } from "../components/PageHeader";
import { useAuth } from "../context/AuthContext";
import { LANGUAGES, useI18n } from "../i18n";
import { useAppDispatch, useAppSelector } from "../store/hooks";
import { savePreferences } from "../store/slices/metaSlice";
import { setTheme } from "../store/slices/uiSlice";
import { errorMessage } from "../utils/errors";
import { APP_NAME, appVersionLabel } from "../utils/appInfo";
import { copyText, getClientInfo, getDeviceId, type ClientInfo } from "../utils/clientInfo";

interface ProfileValues {
  displayName: string;
}

export function SettingsPage() {
  const { t, lang, setLang } = useI18n();
  const { message } = AntdApp.useApp();
  const { user, logout, refreshUser } = useAuth();
  const dispatch = useAppDispatch();
  const themeMode = useAppSelector((state) => state.ui.themeMode);
  const preferences = useAppSelector((state) => state.meta.preferences);
  const [profileForm] = Form.useForm<ProfileValues>();
  const [idleEnabled, setIdleEnabled] = useState(preferences?.idleLockEnabled ?? false);
  const [idleMinutes, setIdleMinutes] = useState(preferences?.idleLockMinutes ?? 15);
  const [savingIdle, setSavingIdle] = useState(false);
  const [dbInfo, setDbInfo] = useState<DatabaseInfo | null>(null);
  const [dbTesting, setDbTesting] = useState(false);
  const [dbMessage, setDbMessage] = useState<{ ok: boolean; text: string } | null>(null);
  const [deviceId] = useState(() =>
    user?.role?.toUpperCase() === "ADMIN" ? getDeviceId() : "",
  );
  const [clientInfo, setClientInfo] = useState<ClientInfo>({ platform: "desktop", system: "—" });
  const isAdmin = user?.role?.toUpperCase() === "ADMIN";

  useEffect(() => {
    profileForm.setFieldsValue({ displayName: user?.displayName ?? "" });
  }, [profileForm, user?.displayName]);

  useEffect(() => {
    if (preferences) {
      setIdleEnabled(preferences.idleLockEnabled);
      setIdleMinutes(preferences.idleLockMinutes);
    }
  }, [preferences]);

  useEffect(() => {
    if (!isAdmin) {
      setDbInfo(null);
      return;
    }
    void databaseApi
      .info()
      .then(setDbInfo)
      .catch(() => setDbInfo(null));
  }, [isAdmin]);

  useEffect(() => {
    let cancelled = false;
    void getClientInfo().then((info) => {
      if (!cancelled) {
        setClientInfo(info);
      }
    });
    return () => {
      cancelled = true;
    };
  }, []);

  const copyDeviceId = async () => {
    try {
      await copyText(deviceId);
      message.success(t("settings.copied"));
    } catch (error) {
      message.error(errorMessage(error));
    }
  };

  const testDatabase = async () => {
    setDbTesting(true);
    setDbMessage(null);
    try {
      const result = await databaseApi.test();
      setDbMessage({
        ok: result.ok,
        text: result.ok ? t("settings.connectionOk") : `${t("settings.connectionFailed")}: ${result.message}`,
      });
    } catch (error) {
      setDbMessage({ ok: false, text: errorMessage(error) });
    } finally {
      setDbTesting(false);
    }
  };

  const saveProfile = async (values: ProfileValues) => {
    try {
      await authApi.updateProfile(values.displayName);
      await refreshUser();
      message.success(t("settings.saved"));
    } catch (error) {
      message.error(errorMessage(error));
    }
  };

  const saveIdle = async () => {
    setSavingIdle(true);
    try {
      await dispatch(
        savePreferences({ idleLockEnabled: idleEnabled, idleLockMinutes: idleMinutes }),
      ).unwrap();
      message.success(t("settings.saved"));
    } catch (error) {
      message.error(errorMessage(error));
    } finally {
      setSavingIdle(false);
    }
  };

  return (
    <>
      <PageHeader eyebrow={t("nav.settings")} title={t("settings.title")} subtitle={t("settings.subtitle")} />

      <div className="settings-grid">
        <section className="setting-card">
          <div className="setting-card-head">
            <UserOutlined />
            <h2>{t("settings.profile")}</h2>
          </div>
          <Form form={profileForm} layout="vertical" onFinish={saveProfile} requiredMark={false}>
            <Form.Item label={t("auth.username")}>
              <Input value={user?.username} disabled />
            </Form.Item>
            <Form.Item name="displayName" label={t("settings.displayName")} rules={[{ required: true }]}>
              <Input />
            </Form.Item>
            <Form.Item label={t("auth.workspace")}>
              <Input value={user?.tenantName} disabled />
            </Form.Item>
            <Button type="primary" htmlType="submit">
              {t("settings.saveProfile")}
            </Button>
          </Form>
        </section>

        <section className="setting-card">
          <div className="setting-card-head">
            <h2>{t("settings.idleLock")}</h2>
          </div>
          <p className="setting-hint">{t("settings.idleLockHint")}</p>
          <div className="setting-row">
            <span>{t("settings.idleLock")}</span>
            <Switch checked={idleEnabled} onChange={setIdleEnabled} />
          </div>
          <div className="setting-row">
            <span>{t("settings.idleMinutes")}</span>
            <InputNumber
              min={1}
              max={240}
              value={idleMinutes}
              onChange={(value) => setIdleMinutes(value ?? 15)}
              style={{ width: 120 }}
            />
          </div>
          <Button loading={savingIdle} onClick={() => void saveIdle()}>
            {t("common.save")}
          </Button>
        </section>

        <section className="setting-card">
          <div className="setting-card-head">
            <h2>{t("settings.appearance")}</h2>
          </div>
          <div className="setting-row">
            <span>{t("settings.theme")}</span>
            <Segmented
              value={themeMode}
              onChange={(value) => dispatch(setTheme(value as "light" | "dark"))}
              options={[
                { label: t("settings.light"), value: "light" },
                { label: t("settings.dark"), value: "dark" },
              ]}
            />
          </div>
          <div className="setting-row">
            <span>{t("settings.language")}</span>
            <Select
              className="lang-select"
              value={lang}
              onChange={(value) => setLang(value as typeof lang)}
              options={LANGUAGES}
            />
          </div>
        </section>

        {isAdmin && (
          <section className="setting-card database-card">
            <div className="setting-card-head">
              <h2>{t("settings.databaseSwitch")}</h2>
            </div>
            <p className="setting-hint">{t("settings.databaseHint")}</p>
            {dbInfo && (
              <dl className="fact-list compact">
                <div className="fact-row">
                  <dt>{t("settings.dbType")}</dt>
                  <dd>{dbInfo.settings.type}</dd>
                </div>
                <div className="fact-row">
                  <dt>{t("settings.dbCurrent")}</dt>
                  <dd className="break">{dbInfo.settings.url || "—"}</dd>
                </div>
                <div className="fact-row">
                  <dt>{t("settings.dbUser")}</dt>
                  <dd>{dbInfo.settings.username || "—"}</dd>
                </div>
                <div className="fact-row">
                  <dt>{t("settings.dbMaxPool")}</dt>
                  <dd>{dbInfo.settings.maxPoolSize}</dd>
                </div>
                <div className="fact-row">
                  <dt>{t("settings.activeProfile")}</dt>
                  <dd className="break">
                    {dbInfo.profile} · {dbInfo.configFile}
                  </dd>
                </div>
              </dl>
            )}
            <div className="setting-actions">
              <Button loading={dbTesting} onClick={() => void testDatabase()}>
                {t("settings.testConnection")}
              </Button>
            </div>
            {dbMessage && (
              <Alert
                style={{ marginTop: 12 }}
                type={dbMessage.ok ? "success" : "error"}
                showIcon
                message={dbMessage.text}
              />
            )}
            <p className="setting-hint" style={{ marginTop: 12 }}>{t("settings.dbConfigHint")}</p>
          </section>
        )}

        <section className="setting-card system-card about-card">
          <div className="about-head">
            <span className="about-icon">
              <InfoCircleOutlined />
            </span>
            <h2>{t("settings.about")}</h2>
            <span className="about-version">
              {t("settings.versionLabel")} {appVersionLabel()}
            </span>
          </div>

          <div className="about-intro">
            <h3>{APP_NAME}</h3>
            <p>{t("settings.aboutDesc")}</p>
          </div>

          <div className="about-meta">
            {isAdmin && (
              <>
                <div className="about-device">
                  <span className="about-label">{t("settings.deviceId")}</span>
                  <code className="about-device-code">{deviceId}</code>
                  <Button
                    type="text"
                    size="small"
                    className="about-copy"
                    icon={<CopyOutlined />}
                    onClick={() => void copyDeviceId()}
                    title={t("settings.copy")}
                    aria-label={t("settings.copy")}
                  />
                </div>
              </>
            )}

            <div className="about-platform">
              <div className="about-platform-row">
                <span>{t("settings.currentPlatform")}</span>
                <strong>
                  {clientInfo.platform === "mobile"
                    ? t("settings.platformMobile")
                    : clientInfo.platform === "tablet"
                      ? t("settings.platformTablet")
                      : t("settings.platformDesktop")}
                </strong>
              </div>
              <div className="about-platform-row">
                <span>{t("settings.os")}</span>
                <strong>{clientInfo.system}</strong>
              </div>
            </div>
          </div>
        </section>
      </div>

      <div className="settings-foot">
        <Button danger icon={<LogoutOutlined />} onClick={() => void logout()}>
          {t("settings.logout")}
        </Button>
      </div>
    </>
  );
}
