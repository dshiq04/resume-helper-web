package com.jobtrack.application.pojo;

import lombok.Data;

import java.util.List;

@Data
public class FunnelResponse {

    private long total;
    private List<FunnelStage> stages;
    private double averageDaysToInterview;
    private double averageInterviewToOffer;
    private List<CompanyFunnel> companies;
    private long interviewCount;
    private long offerCount;

    @Data
    public static class FunnelStage {
        private String key;
        private long count;
        private double conversion;

        public FunnelStage(String key, long count, double conversion) {
            this.key = key;
            this.count = count;
            this.conversion = conversion;
        }
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
