import { useCallback, useEffect, useMemo } from "react";
import { App as AntdApp, ConfigProvider, Spin, Watermark } from "antd";
import { Navigate, Route, Routes, useLocation } from "react-router-dom";
import { useAuth } from "./context/AuthContext";
import { useI18n } from "./i18n";
import { AppLayout } from "./layouts/AppLayout";
import { ApplicationDetailPage } from "./pages/ApplicationDetailPage";
import { DashboardPage } from "./pages/DashboardPage";
import { LoginPage } from "./pages/LoginPage";
import { ResumesPage } from "./pages/ResumesPage";
import { SettingsPage } from "./pages/SettingsPage";
import { StatisticsPage } from "./pages/StatisticsPage";
import { TrashPage } from "./pages/TrashPage";
import { useAppDispatch, useAppSelector } from "./store/hooks";
import { loadPreferences, refreshStats } from "./store/slices/metaSlice";
import { createTheme } from "./theme";

function AppShell() {
  const { user, loading, logout } = useAuth();
  const { t, antdLocale } = useI18n();
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

  const themeConfig = useMemo(() => createTheme(themeMode), [themeMode]);

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
        <Route path="applications/:id" element={<ApplicationDetailPage />} />
        <Route path="*" element={<Navigate to="/" replace />} />
      </Route>
    </Routes>
  );

  return (
    <ConfigProvider theme={themeConfig} locale={antdLocale}>
      <AntdApp>
        {showWatermark ? (
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
          </Watermark>
        ) : (
          body
        )}
      </AntdApp>
    </ConfigProvider>
  );
}

export default function App() {
  return <AppShell />;
}
