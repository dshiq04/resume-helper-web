import { createSlice, type PayloadAction } from "@reduxjs/toolkit";
import type { ThemeMode } from "../../theme";

interface UiState {
  themeMode: ThemeMode;
  sidebarCollapsed: boolean;
}

const THEME_KEY = "jobtrack.theme";
const SIDEBAR_KEY = "jobtrack.sidebar";

function initialTheme(): ThemeMode {
  const saved = localStorage.getItem(THEME_KEY);
  if (saved === "light" || saved === "dark") return saved;
  return window.matchMedia?.("(prefers-color-scheme: dark)").matches ? "dark" : "light";
}

const initialState: UiState = {
  themeMode: initialTheme(),
  sidebarCollapsed: localStorage.getItem(SIDEBAR_KEY) === "1",
};

const uiSlice = createSlice({
  name: "ui",
  initialState,
  reducers: {
    setTheme(state, action: PayloadAction<ThemeMode>) {
      state.themeMode = action.payload;
      localStorage.setItem(THEME_KEY, action.payload);
      document.documentElement.dataset.theme = action.payload;
    },
    toggleTheme(state) {
      const next: ThemeMode = state.themeMode === "light" ? "dark" : "light";
      state.themeMode = next;
      localStorage.setItem(THEME_KEY, next);
      document.documentElement.dataset.theme = next;
    },
    setSidebarCollapsed(state, action: PayloadAction<boolean>) {
      state.sidebarCollapsed = action.payload;
      localStorage.setItem(SIDEBAR_KEY, action.payload ? "1" : "0");
    },
  },
});

export const { setTheme, toggleTheme, setSidebarCollapsed } = uiSlice.actions;
export default uiSlice.reducer;
