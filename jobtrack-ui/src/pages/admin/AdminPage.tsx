import { Result } from "antd";
import { Outlet } from "react-router-dom";
import { PageHeader } from "../../components/PageHeader";
import { useAuth } from "../../context/AuthContext";
import { useI18n } from "../../i18n";
import { isAdminUser } from "../../utils/permissions";

/**
 * 管理员控制台外壳：三个子功能由侧边栏菜单切换，页面顶部不再重复显示 Tabs。
 */
export function AdminPage() {
  const { t } = useI18n();
  const { user } = useAuth();

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
      <Outlet />
    </>
  );
}
