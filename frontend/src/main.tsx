import React from "react";
import ReactDOM from "react-dom/client";
import { App as AntdApp, ConfigProvider } from "antd";
import { Provider } from "react-redux";
import { BrowserRouter } from "react-router-dom";
import App from "./App";
import { AuthProvider } from "./context/AuthContext";
import { I18nProvider, useI18n } from "./i18n";
import { store } from "./store";
import { useAppSelector } from "./store/hooks";
import { createTheme } from "./theme";
import "./styles/index.scss";

/**
 * 全局注册 Ant Design 的国际化与主题。
 * 放在应用最外层，确保登录页、弹窗、日期组件等也跟随设置中的语言切换。
 */
function AntdProviders({ children }: { children: React.ReactNode }) {
  const { antdLocale } = useI18n();
  const themeMode = useAppSelector((state) => state.ui.themeMode);
  const theme = React.useMemo(() => createTheme(themeMode), [themeMode]);

  return (
    <ConfigProvider theme={theme} locale={antdLocale}>
      <AntdApp>{children}</AntdApp>
    </ConfigProvider>
  );
}

ReactDOM.createRoot(document.getElementById("root") as HTMLElement).render(
  <React.StrictMode>
    <Provider store={store}>
      <BrowserRouter>
        <I18nProvider>
          <AntdProviders>
            <AuthProvider>
              <App />
            </AuthProvider>
          </AntdProviders>
        </I18nProvider>
      </BrowserRouter>
    </Provider>
  </React.StrictMode>,
);
