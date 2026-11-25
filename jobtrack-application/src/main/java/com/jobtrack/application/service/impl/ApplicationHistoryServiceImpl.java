package com.jobtrack.application.service.impl;

import com.jobtrack.application.service.ApplicationHistoryService;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.jobtrack.application.pojo.vo.ApplicationChangeVO;
import com.jobtrack.application.pojo.vo.HistoryVO;
import com.jobtrack.application.pojo.po.ApplicationChange;
import com.jobtrack.application.pojo.po.ApplicationRecord;
import com.jobtrack.application.mapper.ApplicationChangeMapper;
import com.jobtrack.common.constant.DomainConstants;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 状态时间线的读写与校正。应用程序表是最终事实来源：
 * 历史与当前状态不一致时，以 applications.status 为准。
 */
@Service
@RequiredArgsConstructor
public class ApplicationHistoryServiceImpl implements ApplicationHistoryService {

    private final ApplicationChangeMapper changeMapper;

    @Transactional
    public void recordStatusChange(Long applicationId, String status, LocalDateTime changedAt) {
        if (applicationId == null || status == null || status.isBlank()) {
            return;
        }
        Long existing = changeMapper.selectCount(Wrappers.<ApplicationChange>lambdaQuery()
                .eq(ApplicationChange::getApplicationId, applicationId)
                .eq(ApplicationChange::getStatus, status));
        if (existing != null && existing > 0) {
            return;
        }
        ApplicationChange change = new ApplicationChange();
        change.setApplicationId(applicationId);
        change.setStatus(status);
        change.setChangedAt(changedAt == null ? LocalDateTime.now() : changedAt);
        try {
            changeMapper.insert(change);
        } catch (DuplicateKeyException ignored) {
            // 并发下的唯一索引冲突等同于“已经记录过”。
        }
    }

    @Transactional
    public void updateStatusChange(Long applicationId, String previousStatus, String status,
                                   LocalDateTime changedAt) {
        if (applicationId == null || status == null || status.isBlank()) {
            return;
        }
        LocalDateTime when = changedAt == null ? LocalDateTime.now() : changedAt;
        ApplicationChange existing = changeMapper.selectOne(Wrappers.<ApplicationChange>lambdaQuery()
                .eq(ApplicationChange::getApplicationId, applicationId)
                .eq(ApplicationChange::getStatus, status));
        if (existing == null) {
            recordStatusChange(applicationId, status, when);
            return;
        }
        if (timelineStatusRank(status) > timelineStatusRank(previousStatus)
                && when.isAfter(existing.getChangedAt())) {
            existing.setChangedAt(when);
            changeMapper.updateById(existing);
        }
    }

    public List<ApplicationChange> loadChanges(Long applicationId) {
        return changeMapper.selectList(Wrappers.<ApplicationChange>lambdaQuery()
                .eq(ApplicationChange::getApplicationId, applicationId)
                .orderByAsc(ApplicationChange::getChangedAt)
                .orderByAsc(ApplicationChange::getId));
    }

    public Map<Long, List<ApplicationChange>> loadChanges(List<Long> applicationIds) {
        Map<Long, List<ApplicationChange>> grouped = new HashMap<>();
        if (applicationIds == null || applicationIds.isEmpty()) {
            return grouped;
        }
        List<ApplicationChange> changes = changeMapper.selectList(Wrappers.<ApplicationChange>lambdaQuery()
                .in(ApplicationChange::getApplicationId, applicationIds)
                .orderByAsc(ApplicationChange::getChangedAt)
                .orderByAsc(ApplicationChange::getId));
        for (ApplicationChange change : changes) {
            grouped.computeIfAbsent(change.getApplicationId(), key -> new ArrayList<>()).add(change);
        }
        return grouped;
    }

    public HistoryVO history(ApplicationRecord record) {
        List<ApplicationChange> changes = reconcile(record, loadChanges(record.getId()));
        sortForTimeline(changes);

        Set<String> present = new HashSet<>();
        for (ApplicationChange change : changes) {
            present.add(change.getStatus());
        }

        List<String> chain = DomainConstants.stageChainForStatus(record.getStatus());
        int currentRank = timelineStatusRank(record.getStatus());
        boolean hasEarlierStage = false;
        for (String status : chain) {
            if (status.equals(record.getStatus()) || timelineStatusRank(status) >= currentRank) {
                continue;
            }
            if (present.contains(status)) {
                hasEarlierStage = true;
                break;
            }
        }

        List<String> missing = new ArrayList<>();
        if (!hasEarlierStage) {
            for (String status : chain) {
                if (!present.contains(status)) {
                    missing.add(status);
                }
            }
        }

        List<ApplicationChangeVO> views = new ArrayList<>();
        LocalDateTime previous = null;
        LocalDateTime now = LocalDateTime.now();
        for (int index = 0; index < changes.size(); index++) {
            ApplicationChange change = changes.get(index);
            ApplicationChangeVO view = ApplicationChangeVO.of(change);
            if (previous != null) {
                view.setDaysSincePrevious(daysBetween(previous, change.getChangedAt()));
            }
            LocalDateTime next = index + 1 < changes.size() ? changes.get(index + 1).getChangedAt() : now;
            view.setDaysInStatus(daysBetween(change.getChangedAt(), next));
            views.add(view);
            previous = change.getChangedAt();
        }

        int screeningDays = 0;
        if (DomainConstants.STATUS_SCREENING.equals(record.getStatus())) {
            LocalDateTime since = record.getAppliedAt();
            for (ApplicationChange change : changes) {
                if (DomainConstants.STATUS_SCREENING.equals(change.getStatus())) {
                    since = change.getChangedAt();
                }
            }
            screeningDays = daysBetween(since, now);
        }

        return new HistoryVO(views, missing, screeningDays, record.getAppliedAt());
    }

    /**
     * 为列表补齐“当前状态停留天数”，一次查询批量完成。
     */
    public void attachStatusDays(List<ApplicationRecord> records) {
        if (records == null || records.isEmpty()) {
            return;
        }
        List<Long> ids = records.stream().map(ApplicationRecord::getId).toList();
        Map<Long, List<ApplicationChange>> grouped = loadChanges(ids);
        LocalDateTime now = LocalDateTime.now();
        for (ApplicationRecord record : records) {
            List<ApplicationChange> changes = grouped.getOrDefault(record.getId(), List.of());
            LocalDateTime since = record.getAppliedAt();
            // 状态可能回退后再次进入（如面试→初筛），应取最后一次进入当前状态的时间。
            for (ApplicationChange change : changes) {
                if (change.getStatus().equals(record.getStatus())) {
                    since = change.getChangedAt();
                }
            }
            int days = daysBetween(since, now);
            record.setStatusDays(days);
            if (DomainConstants.STATUS_SCREENING.equals(record.getStatus())) {
                record.setScreeningDays(days);
            }
        }
    }

    public List<ApplicationChange> reconcile(ApplicationRecord record, List<ApplicationChange> changes) {
        // 不修改传入列表，避免影响调用方的快照。
        List<ApplicationChange> sorted = new ArrayList<>(changes);
        sortChanges(sorted);

        int currentRank = timelineStatusRank(record.getStatus());
        List<ApplicationChange> filtered = new ArrayList<>();
        for (ApplicationChange change : sorted) {
            if (isTerminal(change.getStatus()) && isTerminal(record.getStatus())
                    && !change.getStatus().equals(record.getStatus())) {
                continue;
            }
            if (timelineStatusRank(change.getStatus()) <= currentRank) {
                filtered.add(change);
            }
        }

        boolean foundCurrent = filtered.stream()
                .anyMatch(change -> change.getStatus().equals(record.getStatus()));
        if (!foundCurrent) {
            LocalDateTime changedAt = record.getUpdatedAt();
            if (changedAt == null) {
                changedAt = record.getAppliedAt();
            }
            if (changedAt == null || (!filtered.isEmpty()
                    && changedAt.isBefore(filtered.get(filtered.size() - 1).getChangedAt()))) {
                changedAt = LocalDateTime.now();
            }
            ApplicationChange change = new ApplicationChange();
            change.setApplicationId(record.getId());
            change.setStatus(record.getStatus());
            change.setChangedAt(changedAt);
            filtered.add(change);
        }
        sortForTimeline(filtered);
        return filtered;
    }

    public static int timelineStatusRank(String status) {
        String value = DomainConstants.STATUS_REJECTED.equals(status)
                ? DomainConstants.STATUS_OFFER : status;
        int index = DomainConstants.TIMELINE_ORDER.indexOf(value);
        return index < 0 ? DomainConstants.TIMELINE_ORDER.size() : index;
    }

    public static boolean isTerminal(String status) {
        return DomainConstants.STATUS_OFFER.equals(status)
                || DomainConstants.STATUS_REJECTED.equals(status);
    }

    public static void sortForTimeline(List<ApplicationChange> changes) {
        changes.sort(Comparator
                .comparingInt((ApplicationChange change) -> timelineStatusRank(change.getStatus()))
                .thenComparing(ApplicationChange::getChangedAt,
                        Comparator.nullsLast(Comparator.naturalOrder())));
    }

    private void sortChanges(List<ApplicationChange> changes) {
        changes.sort(Comparator
                .comparing(ApplicationChange::getChangedAt,
                        Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(ApplicationChange::getId,
                        Comparator.nullsLast(Comparator.naturalOrder())));
    }

    public static int daysBetween(LocalDateTime start, LocalDateTime end) {
        if (start == null || end == null) {
            return 0;
        }
        long days = Duration.between(start, end).toDays();
        return days < 0 ? 0 : (int) days;
    }
}
