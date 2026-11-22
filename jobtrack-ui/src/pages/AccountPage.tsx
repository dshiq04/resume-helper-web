import { Tabs } from "antd";
import { Outlet, useLocation, useNavigate } from "react-router-dom";
import { PageHeader } from "../components/PageHeader";
import { useI18n } from "../i18n";

/**
 * 账户设置的二级路由外壳：顶部菜单，内容区域由子路由渲染。
 */
export function AccountPage() {
  const { t } = useI18n();
  const navigate = useNavigate();
  const location = useLocation();
  const path = location.pathname;
  const activeKey = path.includes("/account/cancel")
    ? "cancel"
    : path.endsWith("/tokens")
      ? "tokens"
      : "profile";

  return (
    <>
      <PageHeader
        eyebrow={t("nav.account")}
        title={t("account.title")}
        subtitle={t("account.subtitle")}
      />
      <Tabs
        className="account-tabs"
        activeKey={activeKey}
        onChange={(key) => navigate(`/account/${key}`)}
        items={[
          { key: "profile", label: t("account.profileTab") },
          { key: "tokens", label: t("account.tokensTab") },
          { key: "cancel", label: t("account.cancelTab") },
        ]}
      />
      <Outlet />
    </>
  );
}
