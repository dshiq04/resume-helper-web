package com.jobtrack.application.service.impl;

import com.jobtrack.application.config.CacheNames;
import com.jobtrack.application.service.StatsService;
import com.jobtrack.application.service.ApplicationHistoryService;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.jobtrack.application.pojo.vo.DailyStatsVO;
import com.jobtrack.application.pojo.vo.FunnelVO;
import com.jobtrack.application.pojo.vo.StatsVO;
import com.jobtrack.application.pojo.po.ApplicationChange;
import com.jobtrack.application.pojo.po.ApplicationRecord;
import com.jobtrack.application.mapper.ApplicationRecordMapper;
import com.jobtrack.application.mapper.ResumeMapper;
import com.jobtrack.common.constant.DomainConstants;
import com.jobtrack.common.exception.BusinessException;
import com.jobtrack.common.util.FlexibleTimes;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
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
public class StatsServiceImpl implements StatsService {

    private final ApplicationRecordMapper applicationMapper;
    private final ResumeMapper resumeMapper;
    private final ApplicationHistoryService historyService;

    /**
     * 概览包含“初筛停留天数”等基于当前时间计算的派生值，不放入长 TTL 缓存，
     * 每次请求重新计算，避免天数滞后。
     */
    public StatsVO stats(String from, String to, Integer year) {
        return computeStats(range(from, to, normalizeYear(year)));
    }

    @Cacheable(cacheNames = CacheNames.DAILY_STATS)
    public DailyStatsVO daily(String from, String to, Integer year) {
        return computeDaily(range(from, to, normalizeYear(year)));
    }

    @Cacheable(cacheNames = CacheNames.FUNNEL)
    public FunnelVO funnel(String from, String to, Integer year) {
        return computeFunnel(range(from, to, normalizeYear(year)));
    }

    private StatsVO computeStats(Range range) {
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

        Map<String, Long> byYear = new TreeMap<>(Comparator.reverseOrder());
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
            if (application.getApplyYear() != null) {
                byYear.merge(String.valueOf(application.getApplyYear()), 1L, Long::sum);
            }

            if (DomainConstants.STATUS_SCREENING.equals(application.getStatus())) {
                LocalDateTime since = application.getAppliedAt();
                for (ApplicationChange change : changes.getOrDefault(application.getId(), List.of())) {
                    if (DomainConstants.STATUS_SCREENING.equals(change.getStatus())) {
                        since = change.getChangedAt();
                    }
                }
                int days = daysBetween(since, now);
                screeningCount++;
                screeningDaysSum += days;
                screeningMax = Math.max(screeningMax, days);
            }
        }

        Long resumeCount = resumeMapper.selectCount(null);

        StatsVO response = new StatsVO();
        response.setTotal(total);
        response.setResumes(resumeCount == null ? 0 : resumeCount);
        response.setByCategory(byCategory);
        response.setByStatus(byStatus);
        response.setByCategoryStatus(byCategoryStatus);
        response.setByYear(byYear);
        StatsVO.StuckScreening stuck = new StatsVO.StuckScreening();
        stuck.setCount(screeningCount);
        stuck.setAverageDays(screeningCount == 0 ? 0 : (int) (screeningDaysSum / screeningCount));
        stuck.setMaxDays(screeningMax);
        response.setStuckScreening(stuck);
        return response;
    }

    private DailyStatsVO computeDaily(Range range) {
        List<ApplicationRecord> applications = applications(range);
        Map<String, DailyStatsVO.DailyStat> counts = new TreeMap<>();
        for (ApplicationRecord application : applications) {
            if (application.getAppliedAt() == null) {
                continue;
            }
            String date = application.getAppliedAt().toLocalDate().toString();
            String key = date + "|" + application.getCategory();
            DailyStatsVO.DailyStat item = counts.get(key);
            if (item == null) {
                item = new DailyStatsVO.DailyStat(date, application.getCategory(), 0);
                counts.put(key, item);
            }
            item.setCount(item.getCount() + 1);
        }
        return new DailyStatsVO(new ArrayList<>(counts.values()));
    }

    private FunnelVO computeFunnel(Range range) {
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
            Map<String, LocalDateTime> firstAt = stageTimeline(application.getStatus(), timeline);

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
        List<FunnelVO.FunnelStage> stages = new ArrayList<>();
        stages.add(new FunnelVO.FunnelStage("applied", total, percent(total, total)));
        for (String stage : DomainConstants.STAGE_ORDER) {
            long count = reached.getOrDefault(stage, 0L);
            stages.add(new FunnelVO.FunnelStage(stage, count, percent(count, total)));
        }

        List<FunnelVO.CompanyFunnel> companyFunnels = new ArrayList<>();
        for (Map.Entry<String, CompanyStat> entry : companies.entrySet()) {
            CompanyStat stat = entry.getValue();
            FunnelVO.CompanyFunnel funnel = new FunnelVO.CompanyFunnel();
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
                .comparingLong(FunnelVO.CompanyFunnel::getTotal).reversed()
                .thenComparing(FunnelVO.CompanyFunnel::getCompany));
        if (companyFunnels.size() > 12) {
            companyFunnels = companyFunnels.subList(0, 12);
        }

        FunnelVO response = new FunnelVO();
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

    /**
     * 计算一条投递实际到达过的阶段。
     *
     * <p>旧逻辑直接按“当前状态必经链路”补全阶段，导致所有被淘汰的记录都被算作
     * 到达过面试，初筛数量因此接近累计投递。这里改为只采信真实时间线：
     * 淘汰记录不再反向推断面试/笔试，但仍按实际到达的最高阶段补齐前置阶段，
     * 保证漏斗数量单调递减。</p>
     */
    private Map<String, LocalDateTime> stageTimeline(String currentStatus, List<ApplicationChange> timeline) {
        int currentRank = ApplicationHistoryServiceImpl.timelineStatusRank(currentStatus);
        boolean currentTerminal = ApplicationHistoryServiceImpl.isTerminal(currentStatus);
        Map<String, LocalDateTime> firstAt = new HashMap<>();
        for (ApplicationChange change : timeline) {
            String status = change.getStatus();
            if (status == null) {
                continue;
            }
            // 终态互斥：当前为 Offer 时不采信历史中的淘汰，反之亦然。
            if (currentTerminal && ApplicationHistoryServiceImpl.isTerminal(status)
                    && !status.equals(currentStatus)) {
                continue;
            }
            // 当前状态回退后，不再统计高于当前状态的历史阶段。
            if (ApplicationHistoryServiceImpl.timelineStatusRank(status) > currentRank) {
                continue;
            }
            firstAt.putIfAbsent(status, change.getChangedAt());
        }

        int furthest = -1;
        for (int index = 0; index < DomainConstants.STAGE_ORDER.size(); index++) {
            String stage = DomainConstants.STAGE_ORDER.get(index);
            if (stage.equals(currentStatus) || firstAt.containsKey(stage)) {
                furthest = index;
            }
        }
        if (furthest >= 0) {
            for (int index = 0; index <= furthest; index++) {
                firstAt.putIfAbsent(DomainConstants.STAGE_ORDER.get(index), null);
            }
        }
        return firstAt;
    }

    private List<ApplicationRecord> applications(Range range) {
        var query = Wrappers.<ApplicationRecord>lambdaQuery();
        if (range.year != null) {
            // 年度筛选与投递列表保持一致，按记录自身的 apply_year 过滤，
            // 而不是把年度换算成 applied_at 的起止时间。
            query.eq(ApplicationRecord::getApplyYear, range.year);
        } else {
            if (range.from != null) {
                query.ge(ApplicationRecord::getAppliedAt, range.from);
            }
            if (range.to != null) {
                query.le(ApplicationRecord::getAppliedAt, range.to);
            }
        }
        query.orderByAsc(ApplicationRecord::getAppliedAt).orderByAsc(ApplicationRecord::getId);
        return applicationMapper.selectList(query);
    }

    private Range range(String from, String to, Integer year) {
        LocalDateTime fromTime = parse(from);
        LocalDateTime toTime = parse(to);
        if (fromTime != null && toTime != null && toTime.isBefore(fromTime)) {
            throw BusinessException.badRequest("invalid_range", "结束时间必须晚于开始时间");
        }
        // 只有未指定自定义时间范围时才使用年度筛选；两者互斥，年度优先按 apply_year。
        Integer yearFilter = fromTime == null && toTime == null ? year : null;
        return new Range(fromTime, toTime, yearFilter);
    }

    private Integer normalizeYear(Integer year) {
        if (year == null || year <= 0) {
            return null;
        }
        if (year < 2000 || year > 2100) {
            throw BusinessException.badRequest("invalid_year", "年度需在 2000-2100 之间");
        }
        return year;
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

    private record Range(LocalDateTime from, LocalDateTime to, Integer year) {
    }

    private static final class CompanyStat {
        private long total;
        private long interviews;
        private long offers;
    }
}
