package com.jobtrack.application.service;

import com.jobtrack.application.pojo.po.ApplicationChange;
import com.jobtrack.application.pojo.po.ApplicationRecord;
import com.jobtrack.application.pojo.vo.HistoryVO;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 状态时间线的读写与校正。
 */
public interface ApplicationHistoryService {

    void recordStatusChange(Long applicationId, String status, LocalDateTime changedAt);

    void updateStatusChange(Long applicationId, String previousStatus, String status,
                            LocalDateTime changedAt);

    List<ApplicationChange> loadChanges(Long applicationId);

    Map<Long, List<ApplicationChange>> loadChanges(List<Long> applicationIds);

    HistoryVO history(ApplicationRecord record);

    void attachStatusDays(List<ApplicationRecord> records);

    List<ApplicationChange> reconcile(ApplicationRecord record, List<ApplicationChange> changes);
}
