import { theme as antdTheme, type ThemeConfig } from "antd";

export type ThemeMode = "light" | "dark";

export function createTheme(mode: ThemeMode): ThemeConfig {
  const dark = mode === "dark";
  return {
    algorithm: dark ? antdTheme.darkAlgorithm : antdTheme.defaultAlgorithm,
    token: {
      colorPrimary: dark ? "#a5a3ff" : "#4f46e5",
      colorInfo: dark ? "#a5a3ff" : "#4f46e5",
      colorSuccess: dark ? "#7fd6a4" : "#1f9d63",
      colorWarning: dark ? "#e7b96f" : "#d28a2f",
      colorError: dark ? "#ff8b8b" : "#d84a4a",
      colorTextBase: dark ? "#ececf3" : "#16161d",
      colorBgBase: dark ? "#16161d" : "#ffffff",
      colorBgLayout: dark ? "#0c0c11" : "#f6f7fb",
      colorBgContainer: dark ? "#16161d" : "#ffffff",
      colorBorder: dark ? "#2a2a35" : "#e5e7ef",
      colorBorderSecondary: dark ? "#22222c" : "#eef0f5",
      borderRadius: 10,
      controlHeight: 36,
      fontFamily:
        '"Inter", "SF Pro Text", "Segoe UI", "PingFang SC", "Hiragino Sans GB", "Microsoft YaHei", sans-serif',
      fontSize: 13,
      boxShadow: dark
        ? "0 12px 36px rgba(0, 0, 0, 0.42)"
        : "0 1px 2px rgba(22, 22, 29, 0.04), 0 12px 32px rgba(22, 22, 29, 0.06)",
    },
    components: {
      Button: {
        borderRadius: 9,
        primaryShadow: "none",
        defaultShadow: "none",
        fontWeight: 550,
        controlHeight: 36,
      },
      Card: { borderRadiusLG: 14, paddingLG: 22 },
      Input: { borderRadius: 9, controlHeight: 36 },
      InputNumber: { borderRadius: 9, controlHeight: 36 },
      Select: { borderRadius: 9, controlHeight: 36 },
      DatePicker: { borderRadius: 9, controlHeight: 36 },
      Table: {
        headerBg: "transparent",
        headerColor: dark ? "#9a9aa8" : "#6b7280",
        headerSplitColor: "transparent",
        rowHoverBg: dark ? "#1d1d26" : "#f8f9fd",
        borderColor: dark ? "#232330" : "#eef0f5",
        cellPaddingBlock: 13,
        borderRadius: 12,
      },
      Menu: { itemBorderRadius: 9 },
      Modal: { borderRadiusLG: 16 },
      Drawer: { colorBgElevated: dark ? "#141419" : "#ffffff" },
      Tabs: { itemSelectedColor: dark ? "#a5a3ff" : "#4f46e5" },
      Segmented: {
        itemSelectedBg: dark ? "#262633" : "#ffffff",
        trackBg: dark ? "#1b1b24" : "#f0f1f7",
        borderRadius: 9,
      },
      Popover: { borderRadiusLG: 12 },
      Popconfirm: { borderRadiusLG: 12 },
    },
  };
}
