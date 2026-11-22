import { createAsyncThunk, createSlice } from "@reduxjs/toolkit";
import { authApi } from "../../api/auth";
import { preferencesApi } from "../../api/preferences";
import { statsApi } from "../../api/stats";
import type { OnlineStatus, Preferences, Stats } from "../../api/types";

interface MetaState {
  stats: Stats | null;
  preferences: Preferences | null;
  online: OnlineStatus | null;
  statsLoading: boolean;
}

const initialState: MetaState = {
  stats: null,
  preferences: null,
  online: null,
  statsLoading: false,
};

export const refreshStats = createAsyncThunk("meta/stats", async () => statsApi.overview());
export const loadPreferences = createAsyncThunk("meta/preferences", async () => preferencesApi.get());
export const savePreferences = createAsyncThunk(
  "meta/savePreferences",
  async (input: Partial<Preferences>) => preferencesApi.update(input),
);
export const loadOnline = createAsyncThunk("meta/online", async () => authApi.online());

const metaSlice = createSlice({
  name: "meta",
  initialState,
  reducers: {
    setPreferences(state, action) {
      state.preferences = action.payload;
    },
  },
  extraReducers(builder) {
    builder
      .addCase(refreshStats.pending, (state) => {
        state.statsLoading = true;
      })
      .addCase(refreshStats.fulfilled, (state, action) => {
        state.stats = action.payload;
        state.statsLoading = false;
      })
      .addCase(refreshStats.rejected, (state) => {
        state.statsLoading = false;
      })
      .addCase(loadPreferences.fulfilled, (state, action) => {
        state.preferences = action.payload;
      })
      .addCase(savePreferences.fulfilled, (state, action) => {
        state.preferences = action.payload;
      })
      .addCase(loadOnline.fulfilled, (state, action) => {
        state.online = action.payload;
      });
  },
});

export const { setPreferences } = metaSlice.actions;
export default metaSlice.reducer;
