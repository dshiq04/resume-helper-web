package com.jobtrack.application.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.jobtrack.application.dto.TrashItem;
import com.jobtrack.application.dto.TrashListResponse;
import com.jobtrack.application.dto.TrashPayload;
import com.jobtrack.application.entity.ApplicationChange;
import com.jobtrack.application.entity.ApplicationRecord;
import com.jobtrack.application.entity.DeletedApplication;
import com.jobtrack.application.mapper.ApplicationChangeMapper;
import com.jobtrack.application.mapper.ApplicationRecordMapper;
import com.jobtrack.application.mapper.DeletedApplicationMapper;
import com.jobtrack.common.exception.BusinessException;
import com.jobtrack.common.util.JsonUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class TrashService {

    private final ApplicationRecordMapper applicationMapper;
    private final ApplicationChangeMapper changeMapper;
    private final DeletedApplicationMapper deletedMapper;
    private final ApplicationHistoryService historyService;

    /**
     * 把记录连同时间线整体写入回收站，再删除原记录。
     */
    @Transactional
    public DeletedApplication moveToTrash(ApplicationRecord record) {
        List<ApplicationChange> changes = changeMapper.selectList(Wrappers.<ApplicationChange>lambdaQuery()
                .eq(ApplicationChange::getApplicationId, record.getId())
                .orderByAsc(ApplicationChange::getChangedAt));
        TrashPayload payload = new TrashPayload(record, changes);

        DeletedApplication trash = new DeletedApplication();
        trash.setOriginalId(record.getId());
        trash.setCompanyName(record.getCompanyName());
        trash.setCategory(record.getCategory());
        trash.setStatus(record.getStatus());
        trash.setLink(record.getLink());
        trash.setPayloadJson(JsonUtils.toJson(payload));
        trash.setDeletedAt(LocalDateTime.now());
        deletedMapper.insert(trash);

        changeMapper.delete(Wrappers.<ApplicationChange>lambdaQuery()
                .eq(ApplicationChange::getApplicationId, record.getId()));
        applicationMapper.deleteById(record.getId());
        return trash;
    }

    public TrashListResponse list() {
        List<DeletedApplication> rows = deletedMapper.selectList(Wrappers.<DeletedApplication>lambdaQuery()
                .orderByDesc(DeletedApplication::getDeletedAt)
                .orderByDesc(DeletedApplication::getId));
        List<TrashItem> items = new ArrayList<>();
        for (DeletedApplication row : rows) {
            TrashPayload payload = parsePayload(row);
            TrashItem item = new TrashItem();
            item.setId(row.getId());
            item.setOriginalId(row.getOriginalId());
            item.setDeletedAt(row.getDeletedAt());
            item.setCompanyName(row.getCompanyName());
            item.setCategory(row.getCategory());
            item.setStatus(row.getStatus());
            item.setLink(row.getLink());
            if (payload != null && payload.getApplication() != null) {
                ApplicationRecord application = payload.getApplication();
                item.setCompanyName(application.getCompanyName());
                item.setCategory(application.getCategory());
                item.setStatus(application.getStatus());
                item.setLink(application.getLink());
                item.setPinned(application.getPinned());
                item.setAppliedAt(application.getAppliedAt());
            }
            item.setHistory(payload == null || payload.getHistory() == null
                    ? List.of() : payload.getHistory());
            items.add(item);
        }
        return new TrashListResponse(items, items.size());
    }

    @Transactional
    public ApplicationRecord restore(Long trashId) {
        DeletedApplication trash = deletedMapper.selectById(trashId);
        if (trash == null) {
            throw BusinessException.notFound("not_found", "回收站记录不存在");
        }
        TrashPayload payload = parsePayload(trash);
        if (payload == null || payload.getApplication() == null) {
            throw BusinessException.internal("corrupted_trash", "回收站数据已损坏，无法恢复");
        }

        // 多租户下主键全局唯一，恢复时生成新主键，避免与其他账号冲突。
        ApplicationRecord application = payload.getApplication();
        application.setId(null);
        application.setTenantId(null);
        application.setStatusDays(null);
        application.setScreeningDays(null);
        applicationMapper.insert(application);

        if (payload.getHistory() != null) {
            for (ApplicationChange change : payload.getHistory()) {
                change.setId(null);
                change.setTenantId(null);
                change.setApplicationId(application.getId());
                change.setCreatedAt(null);
                changeMapper.insert(change);
            }
        }

        deletedMapper.deleteById(trashId);
        historyService.attachStatusDays(List.of(application));
        return application;
    }

    public int delete(Long trashId) {
        return deletedMapper.deleteById(trashId);
    }

    public int empty() {
        return deletedMapper.delete(Wrappers.emptyWrapper());
    }

    private TrashPayload parsePayload(DeletedApplication trash) {
        if (trash.getPayloadJson() == null || trash.getPayloadJson().isBlank()) {
            return null;
        }
        try {
            return JsonUtils.fromJson(trash.getPayloadJson(), TrashPayload.class);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }
}
