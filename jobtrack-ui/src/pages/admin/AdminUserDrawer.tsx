import { useEffect, useRef, useState } from "react";
import {
  App as AntdApp,
  Avatar,
  Button,
  DatePicker,
  Drawer,
  Form,
  Input,
  InputNumber,
  Select,
  Spin,
  Tabs,
  Tag,
} from "antd";
import dayjs, { type Dayjs } from "dayjs";
import { adminApi } from "../../api/admin";
import type { AdminUser, AdminUserDetail, UserProfile } from "../../api/types";
import { useI18n } from "../../i18n";
import { COUNTRY_CODES, joinPhone, splitPhone } from "../../utils/countryCodes";
import { errorMessage } from "../../utils/errors";
import { AdminUserTokensPanel } from "./AdminUserTokensPanel";

interface AccountValues {
  username: string;
  displayName: string;
  tenantName: string;
}

interface ContactValues {
  countryCode: string;
  phone: string;
  email: string;
}

interface PasswordValues {
  newPassword: string;
  confirmPassword: string;
}

interface ProfileValues extends Omit<UserProfile, "birthday" | "availableDate"> {
  birthday?: Dayjs;
  availableDate?: Dayjs;
}

function statusColor(status: string) {
  if (status === "BANNED") return "red";
  if (status === "CANCELLED") return "default";
  return "green";
}

export function AdminUserDrawer({
  userId,
  onClose,
  onChanged,
}: {
  userId: number | null;
  onClose: () => void;
  onChanged: () => void;
}) {
  const { t } = useI18n();
  const { message } = AntdApp.useApp();
  const [accountForm] = Form.useForm<AccountValues>();
  const [contactForm] = Form.useForm<ContactValues>();
  const [profileForm] = Form.useForm<ProfileValues>();
  const [passwordForm] = Form.useForm<PasswordValues>();
  const [detail, setDetail] = useState<AdminUserDetail | null>(null);
  const [loading, setLoading] = useState(false);
  const [saving, setSaving] = useState(false);
  const [uploading, setUploading] = useState(false);
  const [tab, setTab] = useState("profile");
  const avatarInput = useRef<HTMLInputElement>(null);

  const user = detail?.user;
  const profile = detail?.profile;

  useEffect(() => {
    if (userId == null) {
      setDetail(null);
      return;
    }
    setLoading(true);
    setTab("profile");
    void adminApi
      .user(userId)
      .then(setDetail)
      .catch((error) => message.error(errorMessage(error)))
      .finally(() => setLoading(false));
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [userId, message]);

  // 详情返回后表单才挂载，这里统一回填一次，避免在未挂载时 setFieldsValue。
  const loadedUserId = detail?.user.id;
  useEffect(() => {
    if (!detail) {
      return;
    }
    accountForm.setFieldsValue({
      username: detail.user.username,
      displayName: detail.user.displayName,
      tenantName: detail.user.tenantName,
    });
    const phone = splitPhone(detail.profile?.phone);
    contactForm.setFieldsValue({
      countryCode: phone.countryCode,
      phone: phone.phone,
      email: detail.profile?.email ?? "",
    });
    profileForm.setFieldsValue({
      ...(detail.profile ?? {}),
      birthday: detail.profile?.birthday ? dayjs(detail.profile.birthday) : undefined,
      availableDate: detail.profile?.availableDate
        ? dayjs(detail.profile.availableDate)
        : undefined,
    });
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [loadedUserId, accountForm, contactForm, profileForm]);

  const saveAccount = async (values: AccountValues) => {
    if (!userId) return;
    setSaving(true);
    try {
      const updated = await adminApi.updateUser(userId, values);
      setDetail((current) => (current ? { ...current, user: updated } : current));
      onChanged();
      message.success(t("admin.saved"));
    } catch (error) {
      message.error(errorMessage(error));
    } finally {
      setSaving(false);
    }
  };

  const saveContact = async (values: ContactValues) => {
    if (!userId) return;
    if (!values.phone.trim() && !values.email.trim()) {
      message.warning(t("auth.contactRequired"));
      return;
    }
    setSaving(true);
    try {
      const local = splitPhone(values.phone).phone;
      const phone = local ? joinPhone(values.countryCode, local) : "";
      const saved = await adminApi.saveContact(userId, {
        countryCode: values.countryCode,
        phone: local ? phone : "",
        email: values.email.trim() || undefined,
      });
      const fallback = splitPhone(saved.phone);
      contactForm.setFieldsValue({
        countryCode: fallback.countryCode,
        phone: fallback.phone,
        email: saved.email ?? "",
      });
      setDetail((current) => (current ? { ...current, profile: saved } : current));
      onChanged();
      message.success(t("admin.saved"));
    } catch (error) {
      message.error(errorMessage(error));
    } finally {
      setSaving(false);
    }
  };

  const saveProfile = async (values: ProfileValues) => {
    if (!userId) return;
    setSaving(true);
    try {
      const saved = await adminApi.saveProfile(userId, {
        ...(profile ?? ({} as UserProfile)),
        ...(values as unknown as UserProfile),
        birthday: values.birthday ? values.birthday.format("YYYY-MM-DD") : null,
        availableDate: values.availableDate
          ? values.availableDate.format("YYYY-MM-DD")
          : null,
      });
      setDetail((current) => (current ? { ...current, profile: saved } : current));
      profileForm.setFieldsValue({
        ...saved,
        birthday: saved.birthday ? dayjs(saved.birthday) : undefined,
        availableDate: saved.availableDate ? dayjs(saved.availableDate) : undefined,
      });
      onChanged();
      message.success(t("admin.saved"));
    } catch (error) {
      message.error(errorMessage(error));
    } finally {
      setSaving(false);
    }
  };

  const uploadAvatar = async (event: React.ChangeEvent<HTMLInputElement>) => {
    const file = event.target.files?.[0];
    event.target.value = "";
    if (!file || !userId) return;
    setUploading(true);
    try {
      const saved = await adminApi.uploadAvatar(userId, file);
      setDetail((current) => (current
        ? {
            ...current,
            user: { ...current.user, avatarUrl: saved.avatarUrl },
            profile: saved,
          }
        : current));
      onChanged();
      message.success(t("settings.saved"));
    } catch (error) {
      message.error(errorMessage(error));
    } finally {
      setUploading(false);
    }
  };

  const resetPassword = async (values: PasswordValues) => {
    if (!userId) return;
    if (values.newPassword !== values.confirmPassword) {
      message.error(t("settings.passwordMismatch"));
      return;
    }
    setSaving(true);
    try {
      const updated = await adminApi.resetPassword(userId, values.newPassword);
      setDetail((current) => (current ? { ...current, user: updated } : current));
      passwordForm.resetFields();
      onChanged();
      message.success(t("admin.passwordReset"));
    } catch (error) {
      message.error(errorMessage(error));
    } finally {
      setSaving(false);
    }
  };

  const statusLabel = (value: string) => {
    if (value === "BANNED") return t("admin.statusBanned");
    if (value === "CANCELLED") return t("admin.statusCancelled");
    return t("admin.statusNormal");
  };

  const profileTab = user && (
    <div className="admin-detail-sections">
      <section className="admin-detail-card">
        <div className="admin-detail-head">
          <Avatar size={64} src={user.avatarUrl || undefined}>
            {(user.displayName || user.username || "U").slice(0, 1)}
          </Avatar>
          <div>
            <strong>{user.displayName || user.username}</strong>
            <span>@{user.username} · {user.tenantName}</span>
          </div>
          <div className="admin-detail-avatar-actions">
            <input
              ref={avatarInput}
              type="file"
              accept="image/png,image/jpeg,image/webp,image/gif"
              hidden
              onChange={(event) => void uploadAvatar(event)}
            />
            <Button loading={uploading} onClick={() => avatarInput.current?.click()}>
              {t("admin.changeAvatar")}
            </Button>
            <p className="setting-hint">{t("admin.avatarHint")}</p>
          </div>
        </div>

        <div className="form-section-label">{t("admin.accountInfo")}</div>
        <Form form={accountForm} layout="vertical" onFinish={saveAccount} requiredMark={false}>
          <div className="form-grid two">
            <Form.Item
              name="username"
              label={t("admin.username")}
              rules={[{ required: true, message: t("auth.username") }]}
            >
              <Input maxLength={32} />
            </Form.Item>
            <Form.Item
              name="displayName"
              label={t("admin.displayName")}
              rules={[{ required: true, message: t("auth.displayName") }]}
            >
              <Input maxLength={64} />
            </Form.Item>
            <Form.Item
              name="tenantName"
              label={t("admin.tenant")}
              rules={[{ required: true, message: t("auth.workspace") }]}
            >
              <Input maxLength={64} />
            </Form.Item>
          </div>
          <Button htmlType="submit" loading={saving}>{t("admin.saveAccount")}</Button>
        </Form>
      </section>

      <section className="admin-detail-card">
        <div className="form-section-label">{t("admin.contact")}</div>
        <Form form={contactForm} layout="vertical" onFinish={saveContact} requiredMark={false}>
          <div className="form-grid two">
            <Form.Item name="countryCode" label={t("auth.countryCode")} initialValue="+86">
              <Select showSearch optionFilterProp="label" options={COUNTRY_CODES} />
            </Form.Item>
            <Form.Item name="phone" label={t("auth.phone")}>
              <Input inputMode="tel" maxLength={24} />
            </Form.Item>
          </div>
          <Form.Item name="email" label={t("auth.email")}>
            <Input autoComplete="email" maxLength={128} />
          </Form.Item>
          <p className="setting-hint">{t("account.contactHint")}</p>
          <Button htmlType="submit" loading={saving}>{t("admin.saveContact")}</Button>
        </Form>
      </section>

      <section className="admin-detail-card">
        <div className="form-section-label">{t("admin.profileInfo")}</div>
        <Form form={profileForm} layout="vertical" onFinish={saveProfile} requiredMark={false}>
          <div className="form-section-label">{t("account.basicInfo")}</div>
          <div className="form-grid two">
            <Form.Item name="realName" label={t("account.realName")}>
              <Input maxLength={64} />
            </Form.Item>
            <Form.Item name="gender" label={t("account.gender")}>
              <Select
                allowClear
                options={[
                  { value: "male", label: t("account.male") },
                  { value: "female", label: t("account.female") },
                  { value: "other", label: t("account.other") },
                ]}
              />
            </Form.Item>
            <Form.Item name="birthday" label={t("account.birthday")}>
              <DatePicker style={{ width: "100%" }} />
            </Form.Item>
            <Form.Item name="location" label={t("account.city")}>
              <Input maxLength={64} />
            </Form.Item>
            <Form.Item name="hometown" label={t("account.hometown")}>
              <Input maxLength={64} />
            </Form.Item>
            <Form.Item name="wechat" label={t("account.wechat")}>
              <Input maxLength={64} />
            </Form.Item>
          </div>

          <div className="form-section-label">{t("account.jobIntention")}</div>
          <div className="form-grid two">
            <Form.Item name="targetPosition" label={t("account.targetPosition")}>
              <Input maxLength={128} />
            </Form.Item>
            <Form.Item name="expectedSalary" label={t("account.expectedSalary")}>
              <Input maxLength={64} placeholder="20-30K" />
            </Form.Item>
            <Form.Item name="workMode" label={t("account.workMode")}>
              <Select
                allowClear
                options={[
                  { value: "onsite", label: t("account.workOnsite") },
                  { value: "hybrid", label: t("account.workHybrid") },
                  { value: "remote", label: t("account.workRemote") },
                ]}
              />
            </Form.Item>
            <Form.Item name="jobStatus" label={t("account.jobStatus")}>
              <Select
                allowClear
                options={[
                  { value: "looking", label: t("account.statusLooking") },
                  { value: "employed", label: t("account.statusEmployed") },
                  { value: "student", label: t("account.statusStudent") },
                ]}
              />
            </Form.Item>
            <Form.Item name="availableDate" label={t("account.availableDate")}>
              <DatePicker style={{ width: "100%" }} />
            </Form.Item>
            <Form.Item name="experienceYears" label={t("account.experienceYears")}>
              <InputNumber min={0} max={60} style={{ width: "100%" }} />
            </Form.Item>
            <Form.Item name="currentCompany" label={t("account.currentCompany")}>
              <Input maxLength={128} />
            </Form.Item>
            <Form.Item name="currentTitle" label={t("account.currentTitle")}>
              <Input maxLength={128} />
            </Form.Item>
          </div>

          <div className="form-section-label">{t("account.education")}</div>
          <div className="form-grid two">
            <Form.Item name="school" label={t("account.school")}>
              <Input maxLength={128} />
            </Form.Item>
            <Form.Item name="major" label={t("account.major")}>
              <Input maxLength={128} />
            </Form.Item>
            <Form.Item name="degree" label={t("account.degree")}>
              <Input maxLength={32} />
            </Form.Item>
            <Form.Item name="graduationYear" label={t("account.graduationYear")}>
              <InputNumber min={1950} max={2100} style={{ width: "100%" }} />
            </Form.Item>
          </div>

          <div className="form-section-label">{t("account.links")}</div>
          <div className="form-grid two">
            <Form.Item name="website" label={t("account.website")}>
              <Input maxLength={255} />
            </Form.Item>
            <Form.Item name="github" label={t("account.github")}>
              <Input maxLength={128} />
            </Form.Item>
            <Form.Item name="linkedin" label={t("account.linkedin")}>
              <Input maxLength={128} />
            </Form.Item>
            <Form.Item name="languages" label={t("account.languages")}>
              <Input maxLength={255} />
            </Form.Item>
            <Form.Item name="skills" label={t("account.skills")} className="span-2">
              <Input.TextArea rows={2} maxLength={1000} />
            </Form.Item>
            <Form.Item name="bio" label={t("account.bio")} className="span-2">
              <Input.TextArea rows={4} maxLength={2000} />
            </Form.Item>
          </div>
          <Button type="primary" htmlType="submit" loading={saving}>
            {t("admin.saveProfile")}
          </Button>
        </Form>
      </section>
    </div>
  );

  const securityTab = user && (
    <section className="admin-detail-card">
      <div className="setting-card-head">
        <h2>{t("admin.passwordTitle")}</h2>
      </div>
      <p className="setting-hint">{t("admin.passwordHint")}</p>
      <Form
        form={passwordForm}
        layout="vertical"
        onFinish={resetPassword}
        requiredMark={false}
        className="admin-password-form"
      >
        <Form.Item
          name="newPassword"
          label={t("admin.newPassword")}
          rules={[
            { required: true, message: t("settings.newPasswordRequired") },
            { min: 6, max: 64, message: t("admin.passwordLength") },
          ]}
        >
          <Input.Password autoComplete="new-password" />
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
          <Input.Password autoComplete="new-password" />
        </Form.Item>
        <Button type="primary" htmlType="submit" loading={saving}>
          {t("admin.resetPassword")}
        </Button>
      </Form>
      <p className="setting-hint">{t("admin.securityHint")}</p>
    </section>
  );

  return (
    <Drawer
      open={userId != null}
      onClose={onClose}
      width="min(960px, 96vw)"
      destroyOnHidden
      className="admin-user-drawer"
      title={(
        <div className="admin-drawer-title">
          <span>{t("admin.detailTitle")}</span>
          {user && (
            <Tag color={statusColor(user.status)}>{statusLabel(user.status)}</Tag>
          )}
        </div>
      )}
    >
      {loading ? (
        <div className="admin-drawer-loading"><Spin /></div>
      ) : user ? (
        <Tabs
          tabPosition="left"
          activeKey={tab}
          onChange={setTab}
          className="admin-detail-tabs"
          items={[
            { key: "profile", label: t("admin.tabProfile"), children: profileTab },
            {
              key: "tokens",
              label: t("admin.tabTokens"),
              children: <AdminUserTokensPanel userId={user.id} />,
            },
            { key: "security", label: t("admin.tabSecurity"), children: securityTab },
          ]}
        />
      ) : null}
    </Drawer>
  );
}
