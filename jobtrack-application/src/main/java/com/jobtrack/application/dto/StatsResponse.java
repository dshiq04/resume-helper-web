package com.jobtrack.application.dto;

import lombok.Data;

import java.util.Map;

@Data
public class StatsResponse {

    private long total;
    private long resumes;
    private Map<String, Long> byCategory;
    private Map<String, Long> byStatus;
    private Map<String, Map<String, Long>> byCategoryStatus;
    private StuckScreening stuckScreening;

    @Data
    public static class StuckScreening {
        private long count;
        private int averageDays;
        private int maxDays;
    }
}
