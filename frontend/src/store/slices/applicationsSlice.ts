import { createAsyncThunk, createSlice, type PayloadAction } from "@reduxjs/toolkit";
import { applicationApi } from "../../api/applications";
import type { Application, Category, ListParams, Status } from "../../api/types";

interface Filters {
  search: string;
  status: Status | "all";
  channel: string;
  year: number | "all";
  page: number;
  pageSize: number;
}

interface ApplicationsState {
  items: Application[];
  total: number;
  years: number[];
  loading: boolean;
  selectedKeys: number[];
  filters: Filters;
}

const initialState: ApplicationsState = {
  items: [],
  total: 0,
  years: [],
  loading: false,
  selectedKeys: [],
  filters: {
    search: "",
    status: "all",
    channel: "",
    year: "all",
    page: 1,
    pageSize: 10,
  },
};

export const fetchApplications = createAsyncThunk(
  "applications/fetch",
  async (params: ListParams) => applicationApi.list(params),
);

export const fetchYears = createAsyncThunk("applications/years", async () => applicationApi.years());

const applicationsSlice = createSlice({
  name: "applications",
  initialState,
  reducers: {
    setFilter(state, action: PayloadAction<Partial<Filters>>) {
      state.filters = { ...state.filters, ...action.payload };
      if (
        action.payload.search !== undefined ||
        action.payload.status !== undefined ||
        action.payload.channel !== undefined ||
        action.payload.year !== undefined
      ) {
        state.filters.page = 1;
      }
    },
    setPage(state, action) {
      state.filters.page = action.payload;
    },
    setPageSize(state, action) {
      state.filters.pageSize = action.payload;
      state.filters.page = 1;
    },
    setSelectedKeys(state, action: PayloadAction<number[]>) {
      state.selectedKeys = action.payload;
    },
    clearSelection(state) {
      state.selectedKeys = [];
    },
    removeLocal(state, action: PayloadAction<number[]>) {
      const ids = new Set(action.payload);
      state.items = state.items.filter((item) => !ids.has(item.id));
      state.total = Math.max(0, state.total - ids.size);
      state.selectedKeys = state.selectedKeys.filter((id) => !ids.has(id));
    },
  },
  extraReducers(builder) {
    builder
      .addCase(fetchApplications.pending, (state) => {
        state.loading = true;
      })
      .addCase(fetchApplications.fulfilled, (state, action) => {
        state.items = action.payload.items;
        state.total = action.payload.total;
        state.filters.page = action.payload.page;
        state.filters.pageSize = action.payload.pageSize;
        state.loading = false;
      })
      .addCase(fetchApplications.rejected, (state) => {
        state.loading = false;
      })
      .addCase(fetchYears.fulfilled, (state, action) => {
        state.years = action.payload.items;
      });
  },
});

export const {
  setFilter,
  setPage,
  setPageSize,
  setSelectedKeys,
  clearSelection,
  removeLocal,
} = applicationsSlice.actions;
export default applicationsSlice.reducer;

export type { Category, Filters };
