import { useEffect, type ReactNode } from "react";
import { setToken } from "../api/client";
import type { AuthResponse } from "../api/types";
import { useAppDispatch, useAppSelector } from "../store/hooks";
import {
  clearSession as clearSessionAction,
  initializeAuth,
  loginUser,
  logoutUser,
  refreshCurrentUser,
  registerUser,
  setUser,
} from "../store/slices/authSlice";
import { resetTabs } from "../store/slices/tabsSlice";

interface LoginInput {
  username: string;
  password: string;
  captchaId?: string;
  captchaCode?: string;
}

interface RegisterInput extends LoginInput {
  confirmPassword?: string;
  displayName?: string;
  tenantName?: string;
  contactType?: "phone" | "email";
  countryCode?: string;
  phone?: string;
  email?: string;
  agreementAccepted?: boolean;
  agreementLanguage?: string;
  agreementTimezone?: string;
}

/**
 * 对外的 useAuth 保持不变，内部状态全部由 Redux Toolkit 管理。
 */
export function AuthProvider({ children }: { children: ReactNode }) {
  const dispatch = useAppDispatch();

  useEffect(() => {
    void dispatch(initializeAuth());
    const handler = () => dispatch(clearSessionAction());
    window.addEventListener("jobtrack:unauthorized", handler);
    return () => window.removeEventListener("jobtrack:unauthorized", handler);
  }, [dispatch]);

  return <>{children}</>;
}

export function useAuth() {
  const dispatch = useAppDispatch();
  const user = useAppSelector((state) => state.auth.user);
  const loading = useAppSelector((state) => state.auth.loading);

  return {
    user,
    loading,
    async login(input: LoginInput) {
      await dispatch(loginUser(input)).unwrap();
      dispatch(resetTabs());
    },
    async register(input: RegisterInput) {
      await dispatch(registerUser(input)).unwrap();
      dispatch(resetTabs());
    },
    async logout() {
      await dispatch(logoutUser()).unwrap();
      dispatch(resetTabs());
    },
    /** 仅清理本地登录态；服务端令牌已失效时使用，避免再发一次注定 401 的请求。 */
    clearSession() {
      dispatch(clearSessionAction());
      dispatch(resetTabs());
    },
    async refreshUser() {
      await dispatch(refreshCurrentUser()).unwrap();
    },
    applySession(session: AuthResponse) {
      setToken(session.token);
      dispatch(setUser(session.user));
    },
  };
}
