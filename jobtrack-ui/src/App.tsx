import { useCallback, useEffect, type ReactNode } from "react";
import { App as AntdApp, Spin, Watermark } from "antd";
import { Navigate, Route, Routes, useLocation, useParams } from "react-router-dom";
import { useAuth } from "./context/AuthContext";
import { useI18n } from "./i18n";
import { AgreementGate } from "./components/AgreementGate";
import { AppLayout } from "./layouts/AppLayout";
import { ApplicationDetailPage } from "./pages/ApplicationDetailPage";
import { AccountPage } from "./pages/AccountPage";
import { AccountProfilePage } from "./pages/AccountProfilePage";
import { AccountTokensPage } from "./pages/AccountTokensPage";
import { AccountCancelEntryPage } from "./pages/AccountCancelEntryPage";
import { AccountCancelPage } from "./pages/AccountCancelPage";
import { DashboardPage } from "./pages/DashboardPage";
import { LoginPage } from "./pages/LoginPage";
import { ResumesPage } from "./pages/ResumesPage";
import { SettingsPage } from "./pages/SettingsPage";
import { StatisticsPage } from "./pages/StatisticsPage";
import { TrashPage } from "./pages/TrashPage";
import { AdminOnlinePage } from "./pages/admin/AdminOnlinePage";
import { AdminOperationsPage } from "./pages/admin/AdminOperationsPage";
import { AdminPage } from "./pages/admin/AdminPage";
import { AdminPermissionsPage } from "./pages/admin/AdminPermissionsPage";
import { AdminUserDetailPage } from "./pages/admin/AdminUserDetailPage";
import { AdminUsersPage } from "./pages/admin/AdminUsersPage";
import { useAppDispatch, useAppSelector } from "./store/hooks";
import { loadPreferences, refreshStats } from "./store/slices/metaSlice";
import {
  canGrantPermissions,
  canManageUsers,
  canViewOnline,
  canViewOperations,
  hasAdminPermission,
  isAdminUser,
} from "./utils/permissions";

/** 管理员路由守卫：按子功能权限决定是否渲染管理员页面。 */
function AdminRoute({ children, permission }: { children: ReactNode; permission?: string }) {
  const { user } = useAuth();
  const allowed = permission
    ? hasAdminPermission(user, permission)
    : isAdminUser(user);
  return allowed ? <>{children}</> : <Navigate to="/" replace />;
}

/** 进入 /admin 时跳到当前账号拥有的第一个管理子页面。 */
function AdminIndexRedirect() {
  const { user } = useAuth();
  if (canManageUsers(user)) return <Navigate to="users" replace />;
  if (canViewOnline(user)) return <Navigate to="online" replace />;
  if (canViewOperations(user)) return <Navigate to="operations" replace />;
  if (canGrantPermissions(user)) return <Navigate to="permissions" replace />;
  return <Navigate to="/" replace />;
}

/** 管理员查看指定用户的投递详情，详情接口会切换到目标用户的工作区。 */
function AdminApplicationDetailRoute() {
  const params = useParams();
  const userId = Number(params.userId);
  if (!Number.isFinite(userId) || userId <= 0) {
    return <Navigate to="/admin/users" replace />;
  }
  return <ApplicationDetailPage adminUserId={userId} />;
}

function AppShell() {
  const { user, loading, logout } = useAuth();
  const { t } = useI18n();
  const { message } = AntdApp.useApp();
  const dispatch = useAppDispatch();
  const location = useLocation();
  const themeMode = useAppSelector((state) => state.ui.themeMode);
  const preferences = useAppSelector((state) => state.meta.preferences);
  const showWatermark = import.meta.env.VITE_SHOW_DEVELOPMENT_WATERMARK === "true";

  useEffect(() => {
    document.documentElement.dataset.theme = themeMode;
  }, [themeMode]);

  const reload = useCallback(() => {
    if (!user) return;
    void dispatch(refreshStats());
    void dispatch(loadPreferences());
  }, [dispatch, user]);

  useEffect(() => {
    reload();
  }, [reload, location.pathname]);

  useEffect(() => {
    const handler = () => {
      if (user) void dispatch(refreshStats());
    };
    window.addEventListener("jobtrack:data-changed", handler);
    return () => window.removeEventListener("jobtrack:data-changed", handler);
  }, [dispatch, user]);

  useEffect(() => {
    if (!user || !preferences?.idleLockEnabled || preferences.idleLockMinutes <= 0) {
      return;
    }
    let timer = 0;
    const reset = () => {
      window.clearTimeout(timer);
      timer = window.setTimeout(() => {
        void logout();
        message.warning(t("error.unauthorized"));
      }, preferences.idleLockMinutes * 60_000);
    };
    const events: Array<keyof WindowEventMap> = [
      "mousemove",
      "mousedown",
      "keydown",
      "wheel",
      "touchstart",
      "scroll",
    ];
    for (const event of events) {
      window.addEventListener(event, reset, { passive: true });
    }
    reset();
    return () => {
      window.clearTimeout(timer);
      for (const event of events) {
        window.removeEventListener(event, reset);
      }
    };
  }, [logout, message, preferences, t, user]);

  const body = loading ? (
    <div className="boot-screen">
      <img className="boot-logo" src="/logo.svg" alt="ResumeTrail" />
      <Spin size="large" />
      <p>{t("common.loading")}</p>
    </div>
  ) : !user ? (
    <LoginPage />
  ) : location.pathname === "/login" || location.pathname === "/register" ? (
    <Navigate to="/" replace />
  ) : location.pathname === "/account/cancel/flow" ? (
    // 申请注销：独立整页（自带顶部菜单），脱离控制台外壳。
    <AccountCancelPage />
  ) : (
    <Routes>
      <Route element={<AppLayout />}>
        <Route index element={<DashboardPage category="all" />} />
        <Route path="spring" element={<DashboardPage category="spring" />} />
        <Route path="autumn" element={<DashboardPage category="autumn" />} />
        <Route path="internship" element={<DashboardPage category="internship" />} />
        <Route path="social" element={<DashboardPage category="social" />} />
        <Route path="resumes" element={<ResumesPage />} />
        <Route path="statistics" element={<StatisticsPage />} />
        <Route path="trash" element={<TrashPage />} />
        <Route path="settings" element={<SettingsPage />} />
        <Route path="account" element={<AccountPage />}>
          <Route index element={<Navigate to="profile" replace />} />
          <Route path="profile" element={<AccountProfilePage />} />
          <Route path="tokens" element={<AccountTokensPage />} />
          <Route path="cancel" element={<AccountCancelEntryPage />} />
        </Route>
        <Route
          path="admin"
          element={(
            <AdminRoute>
              <AdminPage />
            </AdminRoute>
          )}
        >
          <Route index element={<AdminIndexRedirect />} />
          <Route
            path="users"
            element={(
              <AdminRoute permission="ADMIN_USERS">
                <AdminUsersPage />
              </AdminRoute>
            )}
          />
          <Route
            path="users/:id"
            element={(
              <AdminRoute permission="ADMIN_USERS">
                <AdminUserDetailPage />
              </AdminRoute>
            )}
          />
          <Route
            path="users/:userId/applications/:id"
            element={(
              <AdminRoute permission="ADMIN_USERS">
                <AdminApplicationDetailRoute />
              </AdminRoute>
            )}
          />
          <Route
            path="online"
            element={(
              <AdminRoute permission="ADMIN_ONLINE">
                <AdminOnlinePage />
              </AdminRoute>
            )}
          />
          <Route
            path="operations"
            element={(
              <AdminRoute permission="ADMIN_OPERATIONS">
                <AdminOperationsPage />
              </AdminRoute>
            )}
          />
          <Route
            path="permissions"
            element={(
              <AdminRoute permission="ADMIN_PERMISSIONS">
                <AdminPermissionsPage />
              </AdminRoute>
            )}
          />
        </Route>
        <Route path="applications/:id" element={<ApplicationDetailPage />} />
        <Route path="*" element={<Navigate to="/" replace />} />
      </Route>
    </Routes>
  );

  return showWatermark ? (
    <Watermark
      className="app-watermark"
      content="开发中品质，请谅解"
      gap={[140, 140]}
      rotate={-22}
      zIndex={6}
      font={{
        color:
          themeMode === "dark"
            ? "rgba(255, 255, 255, 0.07)"
            : "rgba(22, 22, 29, 0.06)",
        fontSize: 14,
      }}
      style={{ minHeight: "100vh" }}
    >
      {body}
      {user && <AgreementGate />}
    </Watermark>
  ) : (
    <>
      {body}
      {user && <AgreementGate />}
    </>
  );
}

export default function App() {
  return <AppShell />;
}
