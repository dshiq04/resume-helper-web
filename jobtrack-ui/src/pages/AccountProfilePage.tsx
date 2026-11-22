import { useEffect, useRef, useState } from "react";
import {
  App as AntdApp,
  Button,
  DatePicker,
  Form,
  Input,
  InputNumber,
  Segmented,
  Select,
  Tag,
} from "antd";
import dayjs, { type Dayjs } from "dayjs";
import { useNavigate } from "react-router-dom";
import { accountApi } from "../api/account";
import { authApi } from "../api/auth";
import type { UserProfile } from "../api/types";
import { useAuth } from "../context/AuthContext";
import { useI18n } from "../i18n";
import { COUNTRY_CODES, joinPhone, splitPhone } from "../utils/countryCodes";
import { errorMessage } from "../utils/errors";
import {
  EMAIL_PATTERN,
  FULL_PHONE_PATTERN,
  LOCAL_PHONE_PATTERN,
} from "../utils/validation";

interface ProfileValues extends Omit<UserProfile, "birthday" | "availableDate"> {
  birthday?: Dayjs;
  availableDate?: Dayjs;
}

interface ContactValues {
  countryCode: string;
  phone: string;
  email: string;
}

type ContactMethod = "phone" | "email";

interface PasswordValues {
  currentPassword: string;
  contactType: ContactMethod;
  countryCode: string;
  phone: string;
  email: string;
  newPassword: string;
  confirmPassword: string;
}

export function AccountProfilePage() {
  const { t } = useI18n();
  const { message } = AntdApp.useApp();
  const { user, refreshUser, clearSession } = useAuth();
  const navigate = useNavigate();
  const [profileForm] = Form.useForm<ProfileValues>();
  const [contactForm] = Form.useForm<ContactValues>();
  const [passwordForm] = Form.useForm<PasswordValues>();
  const contactType = Form.useWatch("contactType", passwordForm) ?? "phone";
  const [savingProfile, setSavingProfile] = useState(false);
  const [savingContact, setSavingContact] = useState(false);
  const [profileSnapshot, setProfileSnapshot] = useState<UserProfile | null>(null);
  const [avatarUrl, setAvatarUrl] = useState("");
  const [uploadingAvatar, setUploadingAvatar] = useState(false);
  const avatarInput = useRef<HTMLInputElement>(null);
  const [savingPassword, setSavingPassword] = useState(false);

  useEffect(() => {
    void accountApi
      .profile()
      .then((profile) => {
        setAvatarUrl(profile.avatarUrl || "");
        setProfileSnapshot(profile);
        const phone = splitPhone(profile.phone);
        contactForm.setFieldsValue({
          countryCode: phone.countryCode,
          phone: phone.phone,
          email: profile.email ?? "",
        });
        profileForm.setFieldsValue({
          ...profile,
          birthday: profile.birthday ? dayjs(profile.birthday) : undefined,
          availableDate: profile.availableDate ? dayjs(profile.availableDate) : undefined,
        });
      })
      .catch((error) => message.error(errorMessage(error)));
  }, [message, profileForm, contactForm]);

  const uploadAvatar = async (event: React.ChangeEvent<HTMLInputElement>) => {
    const file = event.target.files?.[0];
    event.target.value = "";
    if (!file) {
      return;
    }
    setUploadingAvatar(true);
    try {
      const profile = await accountApi.uploadAvatar(file);
      setAvatarUrl(profile.avatarUrl || "");
      await refreshUser();
      message.success(t("settings.saved"));
    } catch (error) {
      message.error(errorMessage(error));
    } finally {
      setUploadingAvatar(false);
    }
  };

  const saveProfile = async (values: ProfileValues) => {
    setSavingProfile(true);
    try {
      const saved = await accountApi.saveProfile({
        ...(profileSnapshot ?? ({} as UserProfile)),
        ...(values as unknown as UserProfile),
        // 联系方式只能通过「账号与安全 → 联系方式」修改，这里不下发。
        phone: undefined,
        email: undefined,
        birthday: values.birthday ? values.birthday.format("YYYY-MM-DD") : null,
        availableDate: values.availableDate ? values.availableDate.format("YYYY-MM-DD") : null,
      });
      setProfileSnapshot(saved);
      message.success(t("settings.saved"));
    } catch (error) {
      message.error(errorMessage(error));
    } finally {
      setSavingProfile(false);
    }
  };

  const saveContact = async (values: ContactValues) => {
    if (!values.phone.trim() && !values.email.trim()) {
      message.warning(t("auth.contactRequired"));
      return;
    }
    setSavingContact(true);
    try {
      const phone = joinPhone(values.countryCode, values.phone);
      const saved = await accountApi.saveContact({
        countryCode: values.countryCode,
        phone: values.phone.trim() ? phone : "",
        email: values.email.trim(),
      });
      setProfileSnapshot(saved);
      const fallback = splitPhone(saved.phone);
      contactForm.setFieldsValue({
        countryCode: fallback.countryCode,
        phone: fallback.phone,
        email: saved.email ?? "",
      });
      message.success(t("settings.saved"));
    } catch (error) {
      message.error(errorMessage(error));
    } finally {
      setSavingContact(false);
    }
  };

  const changePassword = async (values: PasswordValues) => {
    const isPhone = values.contactType !== "email";
    const contact = isPhone
      ? joinPhone(values.countryCode, values.phone)
      : values.email.trim();
    // 格式不正确时只提示用户修改，不发送请求。
    if (isPhone && !FULL_PHONE_PATTERN.test(contact)) {
      message.error(t("settings.phoneInvalid"));
      return;
    }
    if (!isPhone && !EMAIL_PATTERN.test(contact)) {
      message.error(t("settings.emailInvalid"));
      return;
    }
    if (values.newPassword !== values.confirmPassword) {
      message.error(t("settings.passwordMismatch"));
      return;
    }
    if (values.newPassword === values.currentPassword) {
      message.error(t("settings.passwordSame"));
      return;
    }
    setSavingPassword(true);
    try {
      const result = await authApi.changePassword(
        values.currentPassword,
        values.newPassword,
        contact,
        isPhone ? "phone" : "email",
      );
      if (!result.changed) {
        message.error(t("settings.passwordChangeFailed"));
        return;
      }
      message.success(t("settings.passwordChanged"));
      // 服务端已在修改密码时注销全部会话，这里只清理本地状态并回到登录页。
      clearSession();
      navigate("/login", { replace: true });
    } catch (error) {
      message.error(errorMessage(error));
    } finally {
      setSavingPassword(false);
    }
  };

  const initial = user?.displayName ? Array.from(user.displayName)[0] : "?";
  const statusNormal = (user?.status ?? "NORMAL") !== "CANCELLED";

  return (
    <div className="account-layout">
      <div className="account-top">
        <section className="setting-card account-avatar-card">
          <div className="account-avatar">
            {avatarUrl ? <img src={avatarUrl} alt={t("account.avatar")} /> : initial}
          </div>
          <strong>{user?.displayName}</strong>
          <span className="account-handle">@{user?.username}</span>
          <div className="account-status">
            <span>{t("account.statusLabel")}</span>
            <Tag color={statusNormal ? "green" : "red"}>
              {statusNormal ? t("account.statusNormal") : t("account.statusCancelled")}
            </Tag>
          </div>
          <input
            ref={avatarInput}
            type="file"
            accept="image/png,image/jpeg,image/webp,image/gif"
            hidden
            onChange={(event) => void uploadAvatar(event)}
          />
          <Button loading={uploadingAvatar} onClick={() => avatarInput.current?.click()}>
            {t("account.avatarButton")}
          </Button>
          <p className="setting-hint">{t("account.avatarHint")}</p>
        </section>

        <section className="setting-card account-security-card">
          <div className="setting-card-head">
            <h2>{t("account.security")}</h2>
          </div>

          <div className="form-section-label">{t("account.contact")}</div>
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
            <Button htmlType="submit" loading={savingContact}>
              {t("account.saveContact")}
            </Button>
          </Form>

          <div className="account-divider" />

          <Form
            form={passwordForm}
            layout="vertical"
            onFinish={changePassword}
            onFinishFailed={() => message.warning(t("settings.fixErrors"))}
            requiredMark={false}
          >
            <Form.Item
              name="currentPassword"
              label={t("settings.currentPassword")}
              rules={[{ required: true, message: t("settings.currentPasswordRequired") }]}
            >
              <Input.Password autoComplete="current-password" />
            </Form.Item>
            <Form.Item
              name="contactType"
              label={t("settings.contactMethod")}
              initialValue="phone"
            >
              <Segmented
                options={[
                  { value: "phone", label: t("settings.contactPhone") },
                  { value: "email", label: t("settings.contactEmail") },
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
                  <Select showSearch optionFilterProp="label" options={COUNTRY_CODES} />
                </Form.Item>
                <Form.Item
                  name="phone"
                  label={t("auth.phone")}
                  extra={t("settings.contactHint")}
                  rules={[
                    { required: true, message: t("settings.phoneRequired") },
                    { pattern: LOCAL_PHONE_PATTERN, message: t("settings.phoneInvalid") },
                  ]}
                >
                  <Input inputMode="tel" maxLength={15} placeholder="13800138000" />
                </Form.Item>
              </div>
            ) : (
              <Form.Item
                name="email"
                label={t("auth.email")}
                extra={t("settings.contactHint")}
                rules={[
                  { required: true, message: t("settings.emailRequired") },
                  { type: "email", message: t("settings.emailInvalid") },
                ]}
              >
                <Input autoComplete="email" maxLength={128} />
              </Form.Item>
            )}
            <div className="form-grid two">
              <Form.Item
                name="newPassword"
                label={t("settings.newPassword")}
                rules={[
                  { required: true, message: t("settings.newPasswordRequired") },
                  { min: 6, max: 64, message: t("settings.newPasswordInvalid") },
                  ({ getFieldValue }) => ({
                    validator(_, value) {
                      if (!value || value !== getFieldValue("currentPassword")) {
                        return Promise.resolve();
                      }
                      return Promise.reject(new Error(t("settings.passwordSame")));
                    },
                  }),
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
