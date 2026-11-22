package com.jobtrack.application.pojo.vo;

import lombok.Data;

import java.util.Map;

@Data
public class StatsVO {

    private long total;
    private long resumes;
    private Map<String, Long> byCategory;
    private Map<String, Long> byStatus;
    private Map<String, Map<String, Long>> byCategoryStatus;
    private Map<String, Long> byYear;
    private StuckScreening stuckScreening;

    @Data
    public static class StuckScreening {
        private long count;
        private int averageDays;
        private int maxDays;
    }
}
