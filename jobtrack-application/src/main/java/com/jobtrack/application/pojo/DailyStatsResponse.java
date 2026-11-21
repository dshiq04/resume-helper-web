package com.jobtrack.application.pojo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class DailyStatsResponse {

    private List<DailyStat> daily;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DailyStat {
        private String date;
        private String category;
        private long count;
    }
}
