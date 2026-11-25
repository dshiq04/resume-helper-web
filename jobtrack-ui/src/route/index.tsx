import type { ReactNode } from "react";
import type { RouteObject } from "react-router-dom";
import { Navigate, useParams } from "react-router-dom";
import { useAuth } from "../context/AuthContext";
import { AppLayout } from "../layouts/AppLayout";
import { AccountCancelEntryPage } from "../pages/AccountCancelEntryPage";
import { AccountCancelPage } from "../pages/AccountCancelPage";
import { AccountPage } from "../pages/AccountPage";
import { AccountProfilePage } from "../pages/AccountProfilePage";
import { AccountTokensPage } from "../pages/AccountTokensPage";
import { ApplicationDetailPage } from "../pages/ApplicationDetailPage";
import { DashboardPage } from "../pages/DashboardPage";
import { ResumesPage } from "../pages/ResumesPage";
import { SettingsPage } from "../pages/SettingsPage";
import { StatisticsPage } from "../pages/StatisticsPage";
import { TrashPage } from "../pages/TrashPage";
import { AdminOnlinePage } from "../pages/admin/AdminOnlinePage";
import { AdminOperationsPage } from "../pages/admin/AdminOperationsPage";
import { AdminPage } from "../pages/admin/AdminPage";
import { AdminPermissionsPage } from "../pages/admin/AdminPermissionsPage";
import { AdminUserDetailPage } from "../pages/admin/AdminUserDetailPage";
import { AdminUsersPage } from "../pages/admin/AdminUsersPage";
import {
  canGrantPermissions,
  canManageUsers,
  canViewOnline,
  canViewOperations,
  hasAdminPermission,
  isAdminUser,
} from "../utils/permissions";

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

/**
 * 应用路由表。
 *
 * <p>这里只维护路由属性（路径、页面元素、子路由与权限守卫），
 * 具体渲染交给 App.tsx 的 useRoutes。</p>
 */
export const appRoutes: RouteObject[] = [
  // 已登录时访问登录/注册页，回到工作台。
  { path: "/login", element: <Navigate to="/" replace /> },
  { path: "/register", element: <Navigate to="/" replace /> },
  // 申请注销是独立整页，不套控制台外壳。
  { path: "/account/cancel/flow", element: <AccountCancelPage /> },
  {
    element: <AppLayout />,
    children: [
      { index: true, element: <DashboardPage category="all" /> },
      { path: "spring", element: <DashboardPage category="spring" /> },
      { path: "autumn", element: <DashboardPage category="autumn" /> },
      { path: "internship", element: <DashboardPage category="internship" /> },
      { path: "social", element: <DashboardPage category="social" /> },
      { path: "resumes", element: <ResumesPage /> },
      { path: "statistics", element: <StatisticsPage /> },
      { path: "trash", element: <TrashPage /> },
      { path: "settings", element: <SettingsPage /> },
      {
        path: "account",
        element: <AccountPage />,
        children: [
          { index: true, element: <Navigate to="profile" replace /> },
          { path: "profile", element: <AccountProfilePage /> },
          { path: "tokens", element: <AccountTokensPage /> },
          { path: "cancel", element: <AccountCancelEntryPage /> },
        ],
      },
      {
        path: "admin",
        element: (
          <AdminRoute>
            <AdminPage />
          </AdminRoute>
        ),
        children: [
          { index: true, element: <AdminIndexRedirect /> },
          {
            path: "users",
            element: (
              <AdminRoute permission="ADMIN_USERS">
                <AdminUsersPage />
              </AdminRoute>
            ),
          },
          {
            path: "users/:id",
            element: (
              <AdminRoute permission="ADMIN_USERS">
                <AdminUserDetailPage />
              </AdminRoute>
            ),
          },
          {
            path: "users/:userId/applications/:id",
            element: (
              <AdminRoute permission="ADMIN_USERS">
                <AdminApplicationDetailRoute />
              </AdminRoute>
            ),
          },
          {
            path: "online",
            element: (
              <AdminRoute permission="ADMIN_ONLINE">
                <AdminOnlinePage />
              </AdminRoute>
            ),
          },
          {
            path: "operations",
            element: (
              <AdminRoute permission="ADMIN_OPERATIONS">
                <AdminOperationsPage />
              </AdminRoute>
            ),
          },
          {
            path: "permissions",
            element: (
              <AdminRoute permission="ADMIN_PERMISSIONS">
                <AdminPermissionsPage />
              </AdminRoute>
            ),
          },
        ],
      },
      { path: "applications/:id", element: <ApplicationDetailPage /> },
      { path: "*", element: <Navigate to="/" replace /> },
    ],
  },
];

export default appRoutes;
