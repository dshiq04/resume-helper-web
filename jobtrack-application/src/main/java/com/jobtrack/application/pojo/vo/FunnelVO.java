package com.jobtrack.application.pojo.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
public class FunnelVO {

    private long total;
    private List<FunnelStage> stages;
    private double averageDaysToInterview;
    private double averageInterviewToOffer;
    private List<CompanyFunnel> companies;
    private long interviewCount;
    private long offerCount;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FunnelStage {
        private String key;
        private long count;
        private double conversion;
    }

    @Data
    public static class CompanyFunnel {
        private String company;
        private long total;
        private long interviews;
        private long offers;
        private double interviewRate;
        private double interviewToOfferRate;
        private double offerRate;
    }
}
