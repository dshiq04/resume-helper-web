import { useCallback, useEffect } from "react";
import { App as AntdApp, Spin, Watermark } from "antd";
import { useLocation, useRoutes } from "react-router-dom";
import { useAuth } from "./context/AuthContext";
import { useI18n } from "./i18n";
import { AgreementGate } from "./components/AgreementGate";
import { LoginPage } from "./pages/LoginPage";
import { appRoutes } from "./route";
import { APP_NAME } from "./utils/appInfo";
import { useAppDispatch, useAppSelector } from "./store/hooks";
import { loadPreferences, refreshStats } from "./store/slices/metaSlice";

function AppShell() {
  const { user, loading, logout } = useAuth();
  const { t } = useI18n();
  const { message } = AntdApp.useApp();
  const dispatch = useAppDispatch();
  const location = useLocation();
  const themeMode = useAppSelector((state) => state.ui.themeMode);
  const preferences = useAppSelector((state) => state.meta.preferences);
  const showWatermark = import.meta.env.VITE_SHOW_DEVELOPMENT_WATERMARK === "true";
  // 路由属性集中在 route/index.tsx，这里只负责把配置渲染成页面。
  const routeElement = useRoutes(appRoutes);

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
      <img className="boot-logo" src="/logo.svg" alt={APP_NAME} />
      <Spin size="large" />
      <p>{t("common.loading")}</p>
    </div>
  ) : !user ? (
    <LoginPage />
  ) : (
    routeElement
  );

  return showWatermark ? (
    <Watermark
      className="app-watermark"
      content={import.meta.env.VITE_DEVELOPMENT_WATERMARK_CONTENT}
      gap={[140, 140]}
      rotate={-22}
      zIndex={6}
      font={{
        color:
          themeMode === "dark"
            ? "rgba(255, 255, 255, 0.13)"
            : "rgba(22, 22, 29, 0.12)",
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
