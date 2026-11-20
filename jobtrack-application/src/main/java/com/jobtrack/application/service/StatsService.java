package com.jobtrack.application.service;

import com.jobtrack.application.dto.DailyStatsResponse;
import com.jobtrack.application.dto.FunnelResponse;
import com.jobtrack.application.dto.StatsResponse;
import com.jobtrack.application.entity.ApplicationChange;
import com.jobtrack.application.entity.ApplicationRecord;
import com.jobtrack.application.entity.Resume;
import com.jobtrack.application.mapper.ApplicationRecordMapper;
import com.jobtrack.application.mapper.ResumeMapper;
import com.jobtrack.common.constant.DomainConstants;
import com.jobtrack.common.exception.BusinessException;
import com.jobtrack.common.util.FlexibleTimes;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

@Service
@RequiredArgsConstructor
public class StatsService {

    private final ApplicationRecordMapper applicationMapper;
    private final ResumeMapper resumeMapper;
    private final ApplicationHistoryService historyService;

    public StatsResponse stats(String from, String to) {
        Range range = range(from, to);
        List<ApplicationRecord> applications = applications(range);
        List<Long> ids = applications.stream().map(ApplicationRecord::getId).toList();
        Map<Long, List<ApplicationChange>> changes = historyService.loadChanges(ids);

        Map<String, Long> byCategory = new LinkedHashMap<>();
        Map<String, Long> byStatus = new LinkedHashMap<>();
        Map<String, Map<String, Long>> byCategoryStatus = new LinkedHashMap<>();
        for (String category : DomainConstants.CATEGORIES) {
            byCategory.put(category, 0L);
            Map<String, Long> statusMap = new LinkedHashMap<>();
            for (String status : DomainConstants.STATUSES) {
                statusMap.put(status, 0L);
            }
            byCategoryStatus.put(category, statusMap);
        }
        for (String status : DomainConstants.STATUSES) {
            byStatus.put(status, 0L);
        }

        long total = 0;
        LocalDateTime now = LocalDateTime.now();
        long screeningCount = 0;
        long screeningDaysSum = 0;
        int screeningMax = 0;

        for (ApplicationRecord application : applications) {
            total++;
            byCategory.computeIfPresent(application.getCategory(), (key, value) -> value + 1);
            byStatus.computeIfPresent(application.getStatus(), (key, value) -> value + 1);
            Map<String, Long> statusMap = byCategoryStatus.get(application.getCategory());
            if (statusMap != null) {
                statusMap.computeIfPresent(application.getStatus(), (key, value) -> value + 1);
            }

            if (DomainConstants.STATUS_SCREENING.equals(application.getStatus())) {
                LocalDateTime since = application.getAppliedAt();
                for (ApplicationChange change : changes.getOrDefault(application.getId(), List.of())) {
                    if (DomainConstants.STATUS_SCREENING.equals(change.getStatus())) {
                        since = change.getChangedAt();
                        break;
                    }
                }
                int days = daysBetween(since, now);
                screeningCount++;
                screeningDaysSum += days;
                screeningMax = Math.max(screeningMax, days);
            }
        }

        Long resumeCount = resumeMapper.selectCount(null);

        StatsResponse response = new StatsResponse();
        response.setTotal(total);
        response.setResumes(resumeCount == null ? 0 : resumeCount);
        response.setByCategory(byCategory);
        response.setByStatus(byStatus);
        response.setByCategoryStatus(byCategoryStatus);
        StatsResponse.StuckScreening stuck = new StatsResponse.StuckScreening();
        stuck.setCount(screeningCount);
        stuck.setAverageDays(screeningCount == 0 ? 0 : (int) (screeningDaysSum / screeningCount));
        stuck.setMaxDays(screeningMax);
        response.setStuckScreening(stuck);
        return response;
    }

    public DailyStatsResponse daily(String from, String to) {
        Range range = range(from, to);
        List<ApplicationRecord> applications = applications(range);
        Map<String, DailyStatsResponse.DailyStat> counts = new TreeMap<>();
        for (ApplicationRecord application : applications) {
            if (application.getAppliedAt() == null) {
                continue;
            }
            String date = application.getAppliedAt().toLocalDate().toString();
            String key = date + "|" + application.getCategory();
            DailyStatsResponse.DailyStat item = counts.get(key);
            if (item == null) {
                item = new DailyStatsResponse.DailyStat(date, application.getCategory(), 0);
                counts.put(key, item);
            }
            item.setCount(item.getCount() + 1);
        }
        return new DailyStatsResponse(new ArrayList<>(counts.values()));
    }

    public FunnelResponse funnel(String from, String to) {
        Range range = range(from, to);
        List<ApplicationRecord> applications = applications(range);
        List<Long> ids = applications.stream().map(ApplicationRecord::getId).toList();
        Map<Long, List<ApplicationChange>> changes = historyService.loadChanges(ids);

        Map<String, Long> reached = new HashMap<>();
        Map<String, CompanyStat> companies = new HashMap<>();
        double interviewDaysTotal = 0;
        long interviewDaysCount = 0;
        double offerIntervalTotal = 0;
        long offerIntervalCount = 0;

        for (ApplicationRecord application : applications) {
            List<ApplicationChange> timeline = changes.getOrDefault(application.getId(), List.of());
            Map<String, LocalDateTime> firstAt = new HashMap<>();
            for (ApplicationChange change : timeline) {
                firstAt.putIfAbsent(change.getStatus(), change.getChangedAt());
            }
            // 即使历史缺失，也按当前状态推断已经过的阶段。
            for (String status : DomainConstants.stageChainForStatus(application.getStatus())) {
                firstAt.putIfAbsent(status, null);
            }

            for (String stage : DomainConstants.STAGE_ORDER) {
                if (firstAt.containsKey(stage)) {
                    reached.merge(stage, 1L, Long::sum);
                }
            }

            LocalDateTime interviewAt = firstAt.get(DomainConstants.STATUS_INTERVIEW);
            if (interviewAt != null && application.getAppliedAt() != null) {
                long days = Duration.between(application.getAppliedAt(), interviewAt).toHours() / 24;
                if (days >= 0) {
                    interviewDaysTotal += days;
                    interviewDaysCount++;
                }
                LocalDateTime offerAt = firstAt.get(DomainConstants.STATUS_OFFER);
                if (offerAt != null) {
                    long interval = Duration.between(interviewAt, offerAt).toHours() / 24;
                    if (interval >= 0) {
                        offerIntervalTotal += interval;
                        offerIntervalCount++;
                    }
                }
            }

            String company = application.getCompanyName() == null
                    || application.getCompanyName().isBlank()
                    ? "—" : application.getCompanyName().trim();
            CompanyStat stat = companies.computeIfAbsent(company, key -> new CompanyStat());
            stat.total++;
            if (firstAt.containsKey(DomainConstants.STATUS_INTERVIEW)
                    || firstAt.containsKey(DomainConstants.STATUS_OFFER)) {
                stat.interviews++;
            }
            if (firstAt.containsKey(DomainConstants.STATUS_OFFER)) {
                stat.offers++;
            }
        }

        long total = applications.size();
        List<FunnelResponse.FunnelStage> stages = new ArrayList<>();
        stages.add(new FunnelResponse.FunnelStage("applied", total, percent(total, total)));
        for (String stage : DomainConstants.STAGE_ORDER) {
            long count = reached.getOrDefault(stage, 0L);
            stages.add(new FunnelResponse.FunnelStage(stage, count, percent(count, total)));
        }

        List<FunnelResponse.CompanyFunnel> companyFunnels = new ArrayList<>();
        for (Map.Entry<String, CompanyStat> entry : companies.entrySet()) {
            CompanyStat stat = entry.getValue();
            FunnelResponse.CompanyFunnel funnel = new FunnelResponse.CompanyFunnel();
            funnel.setCompany(entry.getKey());
            funnel.setTotal(stat.total);
            funnel.setInterviews(stat.interviews);
            funnel.setOffers(stat.offers);
            funnel.setInterviewRate(percent(stat.interviews, stat.total));
            funnel.setOfferRate(percent(stat.offers, stat.total));
            funnel.setInterviewToOfferRate(percent(stat.offers, stat.interviews));
            companyFunnels.add(funnel);
        }
        companyFunnels.sort(Comparator
                .comparingLong(FunnelResponse.CompanyFunnel::getTotal).reversed()
                .thenComparing(FunnelResponse.CompanyFunnel::getCompany));
        if (companyFunnels.size() > 12) {
            companyFunnels = companyFunnels.subList(0, 12);
        }

        FunnelResponse response = new FunnelResponse();
        response.setTotal(total);
        response.setStages(stages);
        response.setCompanies(companyFunnels);
        response.setInterviewCount(reached.getOrDefault(DomainConstants.STATUS_INTERVIEW, 0L));
        response.setOfferCount(reached.getOrDefault(DomainConstants.STATUS_OFFER, 0L));
        if (interviewDaysCount > 0) {
            response.setAverageDaysToInterview(round1(interviewDaysTotal / interviewDaysCount));
        }
        if (offerIntervalCount > 0) {
            response.setAverageInterviewToOffer(round1(offerIntervalTotal / offerIntervalCount));
        }
        return response;
    }

    private List<ApplicationRecord> applications(Range range) {
        var query = com.baomidou.mybatisplus.core.toolkit.Wrappers.<ApplicationRecord>lambdaQuery();
        if (range.from != null) {
            query.ge(ApplicationRecord::getAppliedAt, range.from);
        }
        if (range.to != null) {
            query.le(ApplicationRecord::getAppliedAt, range.to);
        }
        query.orderByAsc(ApplicationRecord::getAppliedAt).orderByAsc(ApplicationRecord::getId);
        return applicationMapper.selectList(query);
    }

    private Range range(String from, String to) {
        LocalDateTime fromTime = parse(from);
        LocalDateTime toTime = parse(to);
        if (fromTime != null && toTime != null && toTime.isBefore(fromTime)) {
            throw BusinessException.badRequest("invalid_range", "结束时间必须晚于开始时间");
        }
        return new Range(fromTime, toTime);
    }

    private LocalDateTime parse(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return FlexibleTimes.parse(value);
        } catch (IllegalArgumentException exception) {
            throw BusinessException.badRequest("invalid_range", "日期格式不正确");
        }
    }

    private int daysBetween(LocalDateTime start, LocalDateTime end) {
        if (start == null) {
            return 0;
        }
        long days = Duration.between(start, end).toDays();
        return days < 0 ? 0 : (int) days;
    }

    private double percent(long part, long total) {
        if (total <= 0) {
            return 0;
        }
        return round1(part * 100.0 / total);
    }

    private double round1(double value) {
        return Math.round(value * 10.0) / 10.0;
    }

    private record Range(LocalDateTime from, LocalDateTime to) {
    }

    private static final class CompanyStat {
        private long total;
        private long interviews;
        private long offers;
    }
}
