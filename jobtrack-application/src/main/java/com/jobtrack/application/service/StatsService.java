package com.jobtrack.application.service;

import com.jobtrack.application.pojo.vo.DailyStatsVO;
import com.jobtrack.application.pojo.vo.FunnelVO;
import com.jobtrack.application.pojo.vo.StatsVO;

/**
 * 投递统计。
 */
public interface StatsService {

    StatsVO stats(String from, String to, Integer year);

    DailyStatsVO daily(String from, String to, Integer year);

    FunnelVO funnel(String from, String to, Integer year);
}
