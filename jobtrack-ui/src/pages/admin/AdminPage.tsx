import { Result, Tabs } from "antd";
import { Outlet, useLocation, useNavigate } from "react-router-dom";
import { PageHeader } from "../../components/PageHeader";
import { useAuth } from "../../context/AuthContext";
import { useI18n } from "../../i18n";
import { isAdminUser } from "../../utils/permissions";

/**
 * 管理员控制台外壳：只有拥有管理员控制台权限的账号才能渲染。
 */
export function AdminPage() {
  const { t } = useI18n();
  const { user } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();
  const path = location.pathname;
  const activeKey = path.endsWith("/online")
    ? "online"
    : path.endsWith("/permissions")
      ? "permissions"
      : "users";

  if (!isAdminUser(user)) {
    return <Result status="403" title={t("admin.forbidden")} />;
  }

  return (
    <>
      <PageHeader
        eyebrow={t("admin.group")}
        title={t("admin.title")}
        subtitle={t("admin.subtitle")}
      />
      <Tabs
        className="account-tabs admin-tabs"
        activeKey={activeKey}
        onChange={(key) => navigate(`/admin/${key}`)}
        items={[
          { key: "users", label: t("admin.users") },
          { key: "online", label: t("admin.online") },
          { key: "permissions", label: t("admin.permissions") },
        ]}
      />
      <Outlet />
    </>
  );
}
