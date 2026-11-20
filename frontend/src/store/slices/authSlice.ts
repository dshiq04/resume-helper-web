import { createAsyncThunk, createSlice, type PayloadAction } from "@reduxjs/toolkit";
import { authApi } from "../../api/auth";
import { clearToken, getToken, setToken } from "../../api/client";
import type { AuthResponse, User } from "../../api/types";

interface AuthState {
  user: User | null;
  loading: boolean;
}

const initialState: AuthState = {
  user: null,
  loading: true,
};

export const initializeAuth = createAsyncThunk("auth/initialize", async () => {
  if (!getToken()) {
    return null;
  }
  try {
    return await authApi.me();
  } catch {
    clearToken();
    return null;
  }
});

export const loginUser = createAsyncThunk(
  "auth/login",
  async (input: {
    username: string;
    password: string;
    captchaId?: string;
    captchaCode?: string;
  }) => {
    const session: AuthResponse = await authApi.login(input);
    setToken(session.token);
    return session.user;
  },
);

export const registerUser = createAsyncThunk(
  "auth/register",
  async (input: {
    username: string;
    password: string;
    displayName?: string;
    tenantName?: string;
    captchaId?: string;
    captchaCode?: string;
  }) => {
    const session: AuthResponse = await authApi.register(input);
    setToken(session.token);
    return session.user;
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
        state.user = action.payload;
        state.loading = false;
      })
      .addCase(initializeAuth.rejected, (state) => {
        state.user = null;
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
