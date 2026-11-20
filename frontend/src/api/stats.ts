import { request } from "./client";
import type { DailyStats, FunnelStats, Stats } from "./types";

export interface StatsQuery {
  year?: number | "all";
  from?: string;
  to?: string;
}

/** 对应后端 StatsController。 */
export const statsApi = {
  overview(query?: StatsQuery) {
    return request<Stats>("/stats", { params: normalize(query) });
  },
  daily(query?: StatsQuery) {
    return request<DailyStats>("/stats/daily", { params: normalize(query) });
  },
  funnel(query?: StatsQuery) {
    return request<FunnelStats>("/stats/funnel", { params: normalize(query) });
  },
};

function normalize(query?: StatsQuery) {
  if (!query) return undefined;
  const params: Record<string, string | number> = {};
  if (query.year && query.year !== "all") params.year = query.year;
  if (query.from) params.from = query.from;
  if (query.to) params.to = query.to;
  return Object.keys(params).length > 0 ? params : undefined;
}
