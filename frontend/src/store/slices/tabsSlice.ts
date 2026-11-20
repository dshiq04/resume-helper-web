import { createSlice, type PayloadAction } from "@reduxjs/toolkit";

export interface PageTab {
  path: string;
  labelKey: string;
  closable: boolean;
}

interface TabsState {
  tabs: PageTab[];
  active: string;
}

const STORAGE_KEY = "jobtrack.tabs";

function initialTabs(): PageTab[] {
  try {
    const raw = sessionStorage.getItem(STORAGE_KEY);
    if (raw) {
      const parsed = JSON.parse(raw) as PageTab[];
      if (Array.isArray(parsed) && parsed.length > 0) {
        return parsed;
      }
    }
  } catch {
    // 忽略损坏的会话缓存。
  }
  return [{ path: "/", labelKey: "nav.all", closable: false }];
}

const initialState: TabsState = {
  tabs: initialTabs(),
  active: "/",
};

const tabsSlice = createSlice({
  name: "tabs",
  initialState,
  reducers: {
    syncRoute(state, action: PayloadAction<PageTab>) {
      const tab = action.payload;
      state.active = tab.path;
      if (!state.tabs.some((item) => item.path === tab.path)) {
        state.tabs.push(tab);
      }
      sessionStorage.setItem(STORAGE_KEY, JSON.stringify(state.tabs));
    },
    closeTab(state, action: PayloadAction<string>) {
      const path = action.payload;
      const target = state.tabs.find((item) => item.path === path);
      if (!target || !target.closable) {
        return;
      }
      state.tabs = state.tabs.filter((item) => item.path !== path);
      sessionStorage.setItem(STORAGE_KEY, JSON.stringify(state.tabs));
    },
    setActive(state, action: PayloadAction<string>) {
      state.active = action.payload;
    },
    resetTabs(state) {
      state.tabs = [{ path: "/", labelKey: "nav.all", closable: false }];
      state.active = "/";
      sessionStorage.removeItem(STORAGE_KEY);
    },
  },
});

export const { syncRoute, closeTab, setActive, resetTabs } = tabsSlice.actions;
export default tabsSlice.reducer;
