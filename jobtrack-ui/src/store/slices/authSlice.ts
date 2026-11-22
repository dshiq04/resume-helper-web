import { createAsyncThunk, createSlice, type PayloadAction } from "@reduxjs/toolkit";
import { authApi } from "../../api/auth";
import { ApiError, clearToken, getToken, setToken } from "../../api/client";
import type { AuthResponse, User } from "../../api/types";

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
}

interface AuthError {
  message: string;
  status: number;
  code?: string;
}

/** 转成可序列化的普通对象，避免 thunk 里携带 Error 实例触发 Redux 警告。 */
function toAuthError(error: unknown): AuthError {
  if (error instanceof ApiError) {
    return { message: error.message, status: error.status, code: error.code };
  }
  return {
    message: error instanceof Error ? error.message : "请求失败，请稍后再试",
    status: 0,
    code: "network_error",
  };
}

interface AuthState {
  user: User | null;
  loading: boolean;
}

const initialState: AuthState = {
  user: null,
  loading: true,
};

export const initializeAuth = createAsyncThunk("auth/initialize", async () => {
  const token = getToken();
  if (!token) {
    return null;
  }
  try {
    return await authApi.me();
  } catch {
    // 只清理本次初始化使用的旧令牌，避免覆盖用户刚刚登录拿到的新令牌。
    if (getToken() === token) {
      clearToken();
    }
    return null;
  }
});

export const loginUser = createAsyncThunk<User, LoginInput, { rejectValue: AuthError }>(
  "auth/login",
  async (input, { rejectWithValue }) => {
    try {
      const session: AuthResponse = await authApi.login(input);
      setToken(session.token);
      return session.user;
    } catch (error) {
      // 保留原始 message/code，避免 unwrap() 后只剩通用错误。
      return rejectWithValue(toAuthError(error));
    }
  },
);

export const registerUser = createAsyncThunk<User, RegisterInput, { rejectValue: AuthError }>(
  "auth/register",
  async (input, { rejectWithValue }) => {
    try {
      const session: AuthResponse = await authApi.register(input);
      setToken(session.token);
      return session.user;
    } catch (error) {
      return rejectWithValue(toAuthError(error));
    }
  },
);

export const refreshCurrentUser = createAsyncThunk("auth/refresh", async () => authApi.me());

export const logoutUser = createAsyncThunk("auth/logout", async () => {
  try {
    await authApi.logout();
  } catch {
    // 服务端会话会自然过期，本地清理优先。
  }
  clearToken();
});

const authSlice = createSlice({
  name: "auth",
  initialState,
  reducers: {
    clearSession(state) {
      clearToken();
      state.user = null;
      state.loading = false;
    },
    setUser(state, action: PayloadAction<User>) {
      state.user = action.payload;
    },
  },
  extraReducers(builder) {
    builder
      .addCase(initializeAuth.pending, (state) => {
        state.loading = true;
      })
      .addCase(initializeAuth.fulfilled, (state, action) => {
        // 初始化较慢时用户可能已经手动登录，不能再用旧会话结果覆盖新登录。
        if (!state.user) {
          state.user = action.payload;
        }
        state.loading = false;
      })
      .addCase(initializeAuth.rejected, (state) => {
        if (!state.user) {
          state.user = null;
        }
        state.loading = false;
      })
      .addCase(loginUser.fulfilled, (state, action) => {
        state.user = action.payload;
        state.loading = false;
      })
      .addCase(registerUser.fulfilled, (state, action) => {
        state.user = action.payload;
        state.loading = false;
      })
      .addCase(refreshCurrentUser.fulfilled, (state, action) => {
        state.user = action.payload;
      })
      .addCase(logoutUser.fulfilled, (state) => {
        state.user = null;
        state.loading = false;
      });
  },
});

export const { clearSession, setUser } = authSlice.actions;
export default authSlice.reducer;
