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
  Upload,
} from "antd";
import {
  DownloadOutlined,
  LogoutOutlined,
  UploadOutlined,
  UserOutlined,
} from "@ant-design/icons";
import { authApi } from "../api/auth";
import { databaseApi } from "../api/database";
import { backupApi } from "../api/backup";
import { configApi } from "../api/config";
import { systemApi } from "../api/system";
import type { DatabaseInfo, DatabaseSettings, PublicConfig, SystemInfo } from "../api/types";
import { PageHeader } from "../components/PageHeader";
import { useAuth } from "../context/AuthContext";
import { LANGUAGES, useI18n } from "../i18n";
import { useAppDispatch, useAppSelector } from "../store/hooks";
import { refreshStats, savePreferences } from "../store/slices/metaSlice";
import { setTheme } from "../store/slices/uiSlice";
import { errorMessage } from "../utils/errors";
import { notifyDataChanged } from "../utils/format";

interface ProfileValues {
  displayName: string;
}

interface PasswordValues {
  currentPassword: string;
  newPassword: string;
  confirmPassword: string;
}

export function SettingsPage() {
  const { t, lang, setLang } = useI18n();
  const { message, modal } = AntdApp.useApp();
  const { user, logout, refreshUser } = useAuth();
  const dispatch = useAppDispatch();
  const themeMode = useAppSelector((state) => state.ui.themeMode);
  const online = useAppSelector((state) => state.meta.online);
  const preferences = useAppSelector((state) => state.meta.preferences);
  const [profileForm] = Form.useForm<ProfileValues>();
  const [passwordForm] = Form.useForm<PasswordValues>();
  const [dbForm] = Form.useForm<DatabaseSettings>();
  const [idleEnabled, setIdleEnabled] = useState(preferences?.idleLockEnabled ?? false);
  const [idleMinutes, setIdleMinutes] = useState(preferences?.idleLockMinutes ?? 15);
  const [savingIdle, setSavingIdle] = useState(false);
  const [systemInfo, setSystemInfo] = useState<SystemInfo | null>(null);
  const [config, setConfig] = useState<PublicConfig | null>(null);
  const [importing, setImporting] = useState(false);
  const [dbInfo, setDbInfo] = useState<DatabaseInfo | null>(null);
  const [dbType, setDbType] = useState("sqlite");
  const [dbTesting, setDbTesting] = useState(false);
  const [dbSwitching, setDbSwitching] = useState(false);
  const [dbMessage, setDbMessage] = useState<{ ok: boolean; text: string } | null>(null);

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
    void systemApi.info().then(setSystemInfo).catch(() => setSystemInfo(null));
    void configApi.get().then(setConfig).catch(() => setConfig(null));
    void databaseApi
      .info()
      .then((info) => {
        setDbInfo(info);
        setDbType(info.settings.type);
        dbForm.setFieldsValue(info.settings);
      })
      .catch(() => setDbInfo(null));
  }, [dbForm]);

  const testDatabase = async () => {
    try {
      const values = await dbForm.validateFields();
      setDbTesting(true);
      setDbMessage(null);
      const result = await databaseApi.test(values);
      setDbMessage({
        ok: result.ok,
        text: result.ok ? t("settings.connectionOk") : `${t("settings.connectionFailed")}: ${result.message}`,
      });
    } catch (error) {
      if (error instanceof Error && error.message) {
        // 表单校验失败时不弹出连接提示。
      }
    } finally {
      setDbTesting(false);
    }
  };

  const switchDatabase = async () => {
    try {
      const values = await dbForm.validateFields();
      modal.confirm({
        title: t("settings.switchConfirmTitle"),
        content: t("settings.switchConfirmBody"),
        okText: t("common.confirm"),
        cancelText: t("common.cancel"),
        okButtonProps: { danger: true },
        onOk: async () => {
          setDbSwitching(true);
          try {
            await databaseApi.switchDatabase(values);
            message.success(t("settings.switchDone"));
            await logout();
          } catch (error) {
            message.error(errorMessage(error));
          } finally {
            setDbSwitching(false);
          }
        },
      });
    } catch {
      // 表单校验失败。
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

  const changePassword = async (values: PasswordValues) => {
    if (values.newPassword !== values.confirmPassword) {
      message.error(t("settings.passwordMismatch"));
      return;
    }
    try {
      await authApi.changePassword(values.currentPassword, values.newPassword);
      message.success(t("settings.passwordChanged"));
      await logout();
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

  const importBackup = (file: File) => {
    modal.confirm({
      title: t("settings.import"),
      content: t("settings.importHint"),
      okText: t("common.confirm"),
      cancelText: t("common.cancel"),
      onOk: async () => {
        setImporting(true);
        try {
          const result = await backupApi.importData(file);
          message.success(
            t("settings.importDone", {
              applications: result.applications,
              resumes: result.resumes,
            }),
          );
          notifyDataChanged();
          void dispatch(refreshStats());
        } catch (error) {
          message.error(errorMessage(error));
        } finally {
          setImporting(false);
        }
      },
    });
    return false;
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
            <h2>{t("settings.security")}</h2>
          </div>
          <Form form={passwordForm} layout="vertical" onFinish={changePassword} requiredMark={false}>
            <Form.Item name="currentPassword" label={t("settings.currentPassword")} rules={[{ required: true }]}>
              <Input.Password autoComplete="current-password" />
            </Form.Item>
            <Form.Item
              name="newPassword"
              label={t("settings.newPassword")}
              rules={[{ required: true, min: 6, max: 64 }]}
            >
              <Input.Password autoComplete="new-password" />
            </Form.Item>
            <Form.Item name="confirmPassword" label={t("settings.confirmPassword")} rules={[{ required: true }]}>
              <Input.Password autoComplete="new-password" />
            </Form.Item>
            <Button htmlType="submit">{t("settings.changePassword")}</Button>
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

        <section className="setting-card">
          <div className="setting-card-head">
            <h2>{t("settings.data")}</h2>
          </div>
          <p className="setting-hint">{t("settings.exportHint")}</p>
          <div className="setting-actions">
            <Button icon={<DownloadOutlined />} onClick={() => void backupApi.exportData()}>
              {t("settings.export")}
            </Button>
            <Upload
              accept=".zip"
              showUploadList={false}
              beforeUpload={(file) => importBackup(file)}
            >
              <Button icon={<UploadOutlined />} loading={importing}>
                {t("settings.import")}
              </Button>
            </Upload>
          </div>
          <p className="setting-hint">{t("settings.importHint")}</p>
        </section>

        <section className="setting-card database-card">
          <div className="setting-card-head">
            <h2>{t("settings.databaseSwitch")}</h2>
          </div>
          <p className="setting-hint">{t("settings.databaseHint")}</p>
          {dbInfo && (
            <p className="setting-hint">
              {t("settings.dbCurrent")}：{dbInfo.settings.type}
              {" · "}
              {dbInfo.settings.type === "sqlite"
                ? dbInfo.settings.file
                : `${dbInfo.settings.host}:${dbInfo.settings.port}/${dbInfo.settings.name}`}
            </p>
          )}
          <Form
            form={dbForm}
            layout="vertical"
            className="db-form"
            onValuesChange={(changed) => {
              if (changed.type) {
                setDbType(changed.type);
                dbForm.setFieldValue("port", changed.type === "postgresql" ? 5432 : 3306);
              }
            }}
          >
            <div className="form-grid two">
              <Form.Item name="type" label={t("settings.dbType")} rules={[{ required: true }]}>
                <Select
                  options={[
                    { value: "sqlite", label: "SQLite" },
                    { value: "mysql", label: "MySQL" },
                    { value: "postgresql", label: "PostgreSQL" },
                  ]}
                />
              </Form.Item>
              <Form.Item name="maxPoolSize" label={t("settings.dbMaxPool")}>
                <InputNumber min={1} max={30} style={{ width: "100%" }} />
              </Form.Item>
            </div>

            {dbType === "sqlite" ? (
              <>
                <Form.Item name="file" label={t("settings.dbFile")} rules={[{ required: true }]}>
                  <Input placeholder="jobtrack.db" />
                </Form.Item>
                <Form.Item name="password" label={t("settings.dbPassword")} extra={t("settings.dbPasswordHint")}>
                  <Input.Password placeholder="123456" autoComplete="new-password" />
                </Form.Item>
              </>
            ) : (
              <>
                <div className="form-grid two">
                  <Form.Item name="host" label={t("settings.dbHost")} rules={[{ required: true }]}>
                    <Input placeholder="127.0.0.1" />
                  </Form.Item>
                  <Form.Item name="port" label={t("settings.dbPort")} rules={[{ required: true }]}>
                    <InputNumber min={1} max={65535} style={{ width: "100%" }} />
                  </Form.Item>
                  <Form.Item name="name" label={t("settings.dbName")} rules={[{ required: true }]}>
                    <Input placeholder="jobtrack" />
                  </Form.Item>
                  <Form.Item name="username" label={t("settings.dbUser")}>
                    <Input autoComplete="off" />
                  </Form.Item>
                  <Form.Item name="password" label={t("settings.dbPassword")}>
                    <Input.Password autoComplete="new-password" />
                  </Form.Item>
                  <Form.Item name="params" label={t("settings.dbParams")}>
                    <Input placeholder={dbType === "postgresql" ? "sslmode=disable" : ""} />
                  </Form.Item>
                </div>
              </>
            )}

            <div className="setting-actions">
              <Button loading={dbTesting} onClick={() => void testDatabase()}>
                {t("settings.testConnection")}
              </Button>
              <Button type="primary" danger loading={dbSwitching} onClick={() => void switchDatabase()}>
                {t("settings.switchDatabase")}
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
          </Form>
        </section>

        <section className="setting-card system-card">
          <div className="setting-card-head">
            <h2>{t("settings.system")}</h2>
          </div>
          <dl className="fact-list compact">
            <div className="fact-row">
              <dt>{t("settings.version")}</dt>
              <dd>{systemInfo?.version ?? config?.version ?? "—"}</dd>
            </div>
            <div className="fact-row">
              <dt>{t("settings.onlineMode")}</dt>
              <dd>
                {online?.mode === "redis"
                  ? `${t("settings.redis")} · ${online.onlineUsers} ${t("common.people")}`
                  : t("settings.memory")}
              </dd>
            </div>
            <div className="fact-row">
              <dt>{t("settings.captcha")}</dt>
              <dd>
                {config?.captchaEnabled ? t("settings.captchaOn") : t("settings.captchaOff")}
              </dd>
            </div>
            <div className="fact-row">
              <dt>{t("settings.database")}</dt>
              <dd className="break">{systemInfo?.database || "—"}</dd>
            </div>
            <div className="fact-row">
              <dt>{t("settings.configPath")}</dt>
              <dd className="break">{systemInfo?.configPath || "—"}</dd>
            </div>
            <div className="fact-row">
              <dt>{t("settings.dataDir")}</dt>
              <dd className="break">{systemInfo?.dataDir || "—"}</dd>
            </div>
            <div className="fact-row">
              <dt>Java</dt>
              <dd>{systemInfo?.javaVersion || "—"}</dd>
            </div>
          </dl>
          <p className="setting-hint">{t("settings.captchaHint")}</p>
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
