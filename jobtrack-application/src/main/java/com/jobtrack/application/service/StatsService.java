package com.jobtrack.application.service;

import com.jobtrack.application.pojo.DailyStatsResponse;
import com.jobtrack.application.pojo.FunnelResponse;
import com.jobtrack.application.pojo.StatsResponse;

/**
 * 投递统计。
 */
public interface StatsService {

    StatsResponse stats(String from, String to, Integer year);

    DailyStatsResponse daily(String from, String to, Integer year);

    FunnelResponse funnel(String from, String to, Integer year);
}
