import { useEffect, useState } from "react";
import {
  App as AntdApp,
  Button,
  DatePicker,
  Form,
  Input,
  InputNumber,
  Select,
} from "antd";
import dayjs, { type Dayjs } from "dayjs";
import { accountApi } from "../api/account";
import { authApi } from "../api/auth";
import type { UserProfile } from "../api/types";
import { useAuth } from "../context/AuthContext";
import { useI18n } from "../i18n";
import { errorMessage } from "../utils/errors";

interface ProfileValues extends Omit<UserProfile, "birthday" | "availableDate"> {
  birthday?: Dayjs;
  availableDate?: Dayjs;
}

export function AccountProfilePage() {
  const { t } = useI18n();
  const { message } = AntdApp.useApp();
  const { user, refreshUser, logout } = useAuth();
  const [profileForm] = Form.useForm<ProfileValues>();
  const [usernameForm] = Form.useForm<{ username: string }>();
  const [passwordForm] = Form.useForm<{
    currentPassword: string;
    newPassword: string;
    confirmPassword: string;
  }>();
  const [savingProfile, setSavingProfile] = useState(false);
  const [savingUsername, setSavingUsername] = useState(false);
  const [savingPassword, setSavingPassword] = useState(false);

  useEffect(() => {
    void accountApi
      .profile()
      .then((profile) => {
        profileForm.setFieldsValue({
          ...profile,
          birthday: profile.birthday ? dayjs(profile.birthday) : undefined,
          availableDate: profile.availableDate ? dayjs(profile.availableDate) : undefined,
        });
      })
      .catch((error) => message.error(errorMessage(error)));
  }, [message, profileForm]);

  useEffect(() => {
    usernameForm.setFieldsValue({ username: user?.username ?? "" });
  }, [user?.username, usernameForm]);

  const saveProfile = async (values: ProfileValues) => {
    setSavingProfile(true);
    try {
      await accountApi.saveProfile({
        ...(values as unknown as UserProfile),
        birthday: values.birthday ? values.birthday.format("YYYY-MM-DD") : null,
        availableDate: values.availableDate ? values.availableDate.format("YYYY-MM-DD") : null,
      });
      message.success(t("settings.saved"));
    } catch (error) {
      message.error(errorMessage(error));
    } finally {
      setSavingProfile(false);
    }
  };

  const saveUsername = async (values: { username: string }) => {
    setSavingUsername(true);
    try {
      await accountApi.changeUsername(values.username.trim());
      await refreshUser();
      message.success(t("account.usernameChanged"));
    } catch (error) {
      message.error(errorMessage(error));
    } finally {
      setSavingUsername(false);
    }
  };

  const changePassword = async (values: {
    currentPassword: string;
    newPassword: string;
    confirmPassword: string;
  }) => {
    if (values.newPassword !== values.confirmPassword) {
      message.error(t("settings.passwordMismatch"));
      return;
    }
    setSavingPassword(true);
    try {
      await authApi.changePassword(values.currentPassword, values.newPassword);
      message.success(t("settings.passwordChanged"));
      await logout();
    } catch (error) {
      message.error(errorMessage(error));
    } finally {
      setSavingPassword(false);
    }
  };

  const initial = user?.displayName ? Array.from(user.displayName)[0] : "?";

  return (
    <div className="account-layout">
      <div className="account-top">
        <section className="setting-card account-avatar-card">
          <div className="account-avatar">{initial}</div>
          <strong>{user?.displayName}</strong>
          <span className="account-handle">@{user?.username}</span>
          <Button disabled>{t("account.avatarButton")}</Button>
          <p className="setting-hint">{t("account.avatarHint")}</p>
        </section>

        <section className="setting-card account-security-card">
          <div className="setting-card-head">
            <h2>{t("account.security")}</h2>
          </div>
          <Form form={usernameForm} layout="vertical" onFinish={saveUsername} requiredMark={false}>
            <Form.Item
              name="username"
              label={t("auth.username")}
              extra={t("account.usernameHint")}
              rules={[
                { required: true, message: t("auth.username") },
                {
                  pattern: /^[A-Za-z0-9_.-]{3,32}$/,
                  message: t("account.usernameHint"),
                },
              ]}
            >
              <Input autoComplete="username" />
            </Form.Item>
            <Button htmlType="submit" loading={savingUsername}>
              {t("account.saveUsername")}
            </Button>
          </Form>

          <div className="account-divider" />

          <Form form={passwordForm} layout="vertical" onFinish={changePassword} requiredMark={false}>
            <Form.Item
              name="currentPassword"
              label={t("settings.currentPassword")}
              rules={[{ required: true, message: t("settings.currentPassword") }]}
            >
              <Input.Password autoComplete="current-password" />
            </Form.Item>
            <div className="form-grid two">
              <Form.Item
                name="newPassword"
                label={t("settings.newPassword")}
                rules={[{ required: true, min: 6, message: t("settings.newPassword") }]}
              >
                <Input.Password autoComplete="new-password" />
              </Form.Item>
              <Form.Item
                name="confirmPassword"
                label={t("settings.confirmPassword")}
                rules={[{ required: true, message: t("settings.confirmPassword") }]}
              >
                <Input.Password autoComplete="new-password" />
              </Form.Item>
            </div>
            <Button htmlType="submit" loading={savingPassword}>
              {t("settings.changePassword")}
            </Button>
          </Form>
        </section>
      </div>

      <section className="setting-card account-profile-card">
        <div className="setting-card-head">
          <h2>{t("account.profileTab")}</h2>
        </div>
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
            <Form.Item name="phone" label={t("account.phone")}>
              <Input maxLength={32} />
            </Form.Item>
            <Form.Item name="email" label={t("account.email")}>
              <Input maxLength={128} />
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
              <Input maxLength={255} placeholder="中文 / English" />
            </Form.Item>
            <Form.Item name="skills" label={t("account.skills")} className="span-2">
              <Input.TextArea rows={2} maxLength={1000} />
            </Form.Item>
            <Form.Item name="bio" label={t("account.bio")} className="span-2">
              <Input.TextArea rows={4} maxLength={2000} />
            </Form.Item>
          </div>

          <Button type="primary" htmlType="submit" loading={savingProfile}>
            {t("account.saveProfile")}
          </Button>
        </Form>
      </section>
    </div>
  );
}
