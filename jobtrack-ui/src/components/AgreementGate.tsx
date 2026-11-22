import { useEffect, useState } from "react";
import { App as AntdApp } from "antd";
import { authApi } from "../api/auth";
import type { UserAgreementRecord } from "../api/types";
import { useAuth } from "../context/AuthContext";
import { useI18n } from "../i18n";
import { getLocale, getTimeZone } from "../utils/clientInfo";
import { errorMessage } from "../utils/errors";
import { UserAgreementModal } from "./UserAgreementModal";

/**
 * 登录后强制补签用户协议：没有签署记录时弹出不可关闭的确认框。
 * 确认后服务端写入签署时间、语言、时区、地点与来源 IP。
 */
export function AgreementGate() {
  const { user, logout } = useAuth();
  const { t, lang } = useI18n();
  const { message } = AntdApp.useApp();
  const [record, setRecord] = useState<UserAgreementRecord | null>(null);
  const [checking, setChecking] = useState(true);
  const [accepting, setAccepting] = useState(false);

  useEffect(() => {
    if (!user) {
      setRecord(null);
      setChecking(false);
      return;
    }
    let active = true;
    setChecking(true);
    void authApi
      .agreement()
      .then((value) => {
        if (active) {
          setRecord(value);
        }
      })
      .catch((error) => {
        if (active) {
          message.error(errorMessage(error));
        }
      })
      .finally(() => {
        if (active) {
          setChecking(false);
        }
      });
    return () => {
      active = false;
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [user?.id]);

  const accept = async () => {
    setAccepting(true);
    try {
      const saved = await authApi.acceptAgreement({
        language: lang,
        timezone: getTimeZone(),
        locale: getLocale(),
      });
      setRecord(saved);
      message.success(t("agreement.accepted"));
    } catch (error) {
      message.error(errorMessage(error));
    } finally {
      setAccepting(false);
    }
  };

  const open = Boolean(user) && !checking && record?.signed === false;

  return (
    <UserAgreementModal
      open={open}
      required
      confirmLoading={accepting}
      onAgree={accept}
      onClose={() => undefined}
      onLogout={() => void logout()}
    />
  );
}
