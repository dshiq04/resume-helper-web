import { configureStore } from "@reduxjs/toolkit";
import applicationsReducer from "./slices/applicationsSlice";
import authReducer from "./slices/authSlice";
import metaReducer from "./slices/metaSlice";
import tabsReducer from "./slices/tabsSlice";
import uiReducer from "./slices/uiSlice";

export const store = configureStore({
  reducer: {
    auth: authReducer,
    ui: uiReducer,
    meta: metaReducer,
    tabs: tabsReducer,
    applications: applicationsReducer,
  },
});

export type RootState = ReturnType<typeof store.getState>;
export type AppDispatch = typeof store.dispatch;
