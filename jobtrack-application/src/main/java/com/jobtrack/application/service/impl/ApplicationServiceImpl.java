package com.jobtrack.application.service.impl;

import com.jobtrack.application.service.ApplicationService;
import com.jobtrack.application.service.ApplicationCacheService;
import com.jobtrack.application.service.TrashService;
import com.jobtrack.application.service.ApplicationHistoryService;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.jobtrack.application.pojo.ApplicationChangeView;
import com.jobtrack.application.pojo.ApplicationPageCache;
import com.jobtrack.application.pojo.ApplicationRequest;
import com.jobtrack.application.pojo.ApplicationUpdateRequest;
import com.jobtrack.application.pojo.HistoryResponse;
import com.jobtrack.application.pojo.YearList;
import com.jobtrack.application.pojo.ApplicationChange;
import com.jobtrack.application.pojo.ApplicationRecord;
import com.jobtrack.application.mapper.ApplicationChangeMapper;
import com.jobtrack.application.mapper.ApplicationRecordMapper;
import com.jobtrack.common.api.PageResult;
import com.jobtrack.common.constant.DomainConstants;
import com.jobtrack.common.exception.BusinessException;
import com.jobtrack.common.util.FlexibleTimes;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class ApplicationServiceImpl implements ApplicationService {

    private final ApplicationRecordMapper applicationMapper;
    private final ApplicationChangeMapper changeMapper;
    private final ApplicationHistoryService historyService;
    private final TrashService trashService;
    private final ApplicationCacheService applicationCache;

    /**
     * 投递列表优先读取 Redis 缓存（未启用或未命中时查询数据库）。
     */
    public PageResult<ApplicationRecord> list(String search, String category, String status,
                                              String channel, String priority, Integer year,
                                              String from, String to, int page, int pageSize) {
        int currentPage = Math.max(page, 1);
        int currentSize = Math.min(Math.max(pageSize, 1), 100);
        Integer normalizedYear = normalizeYear(year);
        String cacheKey = String.join("|", "list",
                String.valueOf(normalizedYear), normalizeKey(search), normalizeKey(category),
                normalizeKey(status), normalizeKey(channel), normalizeKey(priority),
                normalizeKey(from), normalizeKey(to), String.valueOf(currentPage),
                String.valueOf(currentSize));

        ApplicationPageCache cached = applicationCache.cached(cacheKey, ApplicationPageCache.class,
                () -> queryPage(search, category, status, channel, priority, normalizedYear,
                        from, to, currentPage, currentSize));
        return PageResult.of(cached.getItems(), cached.getTotal(), cached.getPage(), cached.getPageSize());
    }

    /**
     * 年度选项同样走缓存，页面打开时只查一次数据库。
     */
    public YearList years() {
        return applicationCache.cached("years", YearList.class, () -> {
            QueryWrapper<ApplicationRecord> query = new QueryWrapper<>();
            query.select("DISTINCT apply_year").isNotNull("apply_year").orderByDesc("apply_year");
            List<Object> values = applicationMapper.selectObjs(query);
            List<Integer> years = new ArrayList<>();
            for (Object value : values) {
                if (value instanceof Number number) {
                    years.add(number.intValue());
                } else if (value != null) {
                    years.add(Integer.parseInt(String.valueOf(value)));
                }
            }
            return new YearList(years);
        });
    }

    private ApplicationPageCache queryPage(String search, String category, String status,
                                           String channel, String priority, Integer year,
                                           String from, String to, int page, int pageSize) {
        QueryWrapper<ApplicationRecord> query = new QueryWrapper<>();
        String normalizedCategory = normalizeFilter(category);
        String normalizedStatus = normalizeFilter(status);
        if (!normalizedCategory.isEmpty()) {
            requireCategory(normalizedCategory);
            query.eq("category", normalizedCategory);
        }
        if (!normalizedStatus.isEmpty()) {
            requireStatus(normalizedStatus);
            query.eq("status", normalizedStatus);
        }
        if (channel != null && !channel.isBlank()) {
            query.eq("channel", channel.trim());
        }
        if (priority != null && !priority.isBlank()) {
            query.eq("priority", priority.trim());
        }
        if (year != null) {
            query.eq("apply_year", year);
        }
        if (search != null && !search.isBlank()) {
            String like = "%" + search.trim().toLowerCase() + "%";
            query.and(wrapper -> wrapper
                    .apply("LOWER(company_name) LIKE {0}", like)
                    .or().apply("LOWER(link) LIKE {0}", like)
                    .or().apply("LOWER(job_title) LIKE {0}", like)
                    .or().apply("LOWER(notes) LIKE {0}", like));
        }
        LocalDateTime fromTime = parseOptional(from);
        LocalDateTime toTime = parseOptional(to);
        if (fromTime != null && toTime != null && toTime.isBefore(fromTime)) {
            throw BusinessException.badRequest("invalid_range", "结束时间必须晚于开始时间");
        }
        if (fromTime != null) {
            query.ge("applied_at", fromTime);
        }
        if (toTime != null) {
            query.le("applied_at", toTime);
        }
        query.orderByDesc("pinned").orderByDesc("applied_at").orderByDesc("id");

        Page<ApplicationRecord> result = applicationMapper.selectPage(new Page<>(page, pageSize), query);
        historyService.attachStatusDays(result.getRecords());
        return new ApplicationPageCache(result.getRecords(), result.getTotal(), page, pageSize);
    }

    @Transactional
    public ApplicationRecord create(ApplicationRequest request) {
        ApplicationRecord record = new ApplicationRecord();
        String category = normalizeFilter(request.getCategory());
        requireCategory(category);
        record.setCategory(category);
        record.setCompanyName(requireCompany(request.getCompanyName()));
        record.setLink(requireLink(request.getLink()));
        record.setPosition(trimToNull(request.getPosition()));
        String status = normalizeFilter(request.getStatus());
        requireStatus(status);
        record.setStatus(status);
        record.setChannel(trimToNull(request.getChannel()));
        record.setCity(trimToNull(request.getCity()));
        record.setSalary(trimToNull(request.getSalary()));
        record.setContact(trimToNull(request.getContact()));
        record.setContactInfo(trimToNull(request.getContactInfo()));
        record.setWorkMode(trimToNull(request.getWorkMode()));
        record.setPriority(trimToNull(request.getPriority()));
        record.setInterviewRound(request.getInterviewRound());
        record.setNextActionAt(request.getNextActionAt());
        record.setNotes(trimToNull(request.getNotes()));
        record.setPinned(Boolean.TRUE.equals(request.getPinned()));
        LocalDateTime appliedAt = request.getAppliedAt() == null
                ? LocalDateTime.now() : request.getAppliedAt();
        record.setAppliedAt(appliedAt);
        Integer requestedYear = normalizeYear(request.getYear());
        if (requestedYear == null) {
            throw BusinessException.badRequest("year_required", "请选择年度");
        }
        record.setApplyYear(requestedYear);
        applicationMapper.insert(record);

        // 时间线始终从“待定”开始，再记录真实状态。
        historyService.recordStatusChange(record.getId(), DomainConstants.STATUS_PENDING, record.getAppliedAt());
        historyService.recordStatusChange(record.getId(), record.getStatus(), record.getAppliedAt());
        historyService.attachStatusDays(List.of(record));
        applicationCache.invalidate();
        return record;
    }

    public ApplicationRecord get(Long id) {
        ApplicationRecord record = applicationMapper.selectById(id);
        if (record == null) {
            throw BusinessException.notFound("not_found", "投递记录不存在");
        }
        historyService.attachStatusDays(List.of(record));
        return record;
    }

    @Transactional
    public ApplicationRecord update(Long id, ApplicationUpdateRequest request) {
        // 修改前先确认记录存在，不存在按 500 返回明确提示。
        ApplicationRecord record = applicationMapper.selectById(id);
        if (record == null) {
            throw BusinessException.internal("record_not_found", "记录不存在");
        }
        String previousStatus = record.getStatus();

        String category = normalizeFilter(request.getCategory());
        requireCategory(category);
        record.setCategory(category);
        if (request.getCompanyName() != null) {
            record.setCompanyName(requireCompany(request.getCompanyName()));
        }
        if (request.getLink() != null) {
            record.setLink(requireLink(request.getLink()));
        }
        if (request.getPosition() != null) {
            record.setPosition(trimToNull(request.getPosition()));
        }
        String status = normalizeFilter(request.getStatus());
        requireStatus(status);
        record.setStatus(status);
        if (request.getChannel() != null) {
            record.setChannel(trimToNull(request.getChannel()));
        }
        if (request.getCity() != null) {
            record.setCity(trimToNull(request.getCity()));
        }
        if (request.getSalary() != null) {
            record.setSalary(trimToNull(request.getSalary()));
        }
        if (request.getContact() != null) {
            record.setContact(trimToNull(request.getContact()));
        }
        if (request.getContactInfo() != null) {
            record.setContactInfo(trimToNull(request.getContactInfo()));
        }
        if (request.getWorkMode() != null) {
            record.setWorkMode(trimToNull(request.getWorkMode()));
        }
        if (request.getPriority() != null) {
            record.setPriority(trimToNull(request.getPriority()));
        }
        if (request.getInterviewRound() != null) {
            record.setInterviewRound(request.getInterviewRound());
        }
        if (request.getNextActionAt() != null) {
            record.setNextActionAt(request.getNextActionAt());
        }
        if (request.getNotes() != null) {
            record.setNotes(trimToNull(request.getNotes()));
        }
        if (request.getPinned() != null) {
            record.setPinned(request.getPinned());
        }
        Integer requestedYear = normalizeYear(request.getYear());
        if (requestedYear == null) {
            throw BusinessException.badRequest("year_required", "请选择年度");
        }
        record.setApplyYear(requestedYear);

        applicationMapper.updateById(record);
        if (!record.getStatus().equals(previousStatus)) {
            historyService.updateStatusChange(record.getId(), previousStatus, record.getStatus(),
                    LocalDateTime.now());
        }
        ApplicationRecord reloaded = applicationMapper.selectById(id);
        if (reloaded == null) {
            throw BusinessException.internal("record_not_found", "记录不存在");
        }
        historyService.attachStatusDays(List.of(reloaded));
        applicationCache.invalidate();
        return reloaded;
    }

    @Transactional
    public void delete(Long id) {
        // 删除前确认记录存在，不存在时返回 500 并回滚。
        ApplicationRecord record = applicationMapper.selectById(id);
        if (record == null) {
            throw BusinessException.internal("record_not_found", "记录不存在");
        }
        trashService.moveToTrash(record);
        applicationCache.invalidate();
    }

    @Transactional
    public int batchDelete(List<Long> ids) {
        List<Long> unique = uniqueIds(ids);
        if (unique.isEmpty()) {
            throw BusinessException.badRequest("ids_required", "请至少选择一条记录");
        }
        if (unique.size() > 2000) {
            throw BusinessException.badRequest("too_many_ids", "一次最多删除 2000 条记录");
        }
        List<ApplicationRecord> records = applicationMapper.selectByIds(unique);
        int deleted = 0;
        for (ApplicationRecord record : records) {
            trashService.moveToTrash(record);
            deleted++;
        }
        applicationCache.invalidate();
        return deleted;
    }

    @Transactional
    public int batchStatus(List<Long> ids, String status) {
        List<Long> unique = uniqueIds(ids);
        if (unique.isEmpty()) {
            throw BusinessException.badRequest("ids_required", "请至少选择一条记录");
        }
        String normalized = statusOrDefault(status, "");
        List<ApplicationRecord> records = applicationMapper.selectByIds(unique);
        int updated = 0;
        for (ApplicationRecord record : records) {
            if (normalized.equals(record.getStatus())) {
                continue;
            }
            String previous = record.getStatus();
            record.setStatus(normalized);
            applicationMapper.updateById(record);
            historyService.updateStatusChange(record.getId(), previous, normalized, LocalDateTime.now());
            updated++;
        }
        applicationCache.invalidate();
        return updated;
    }

    @Transactional
    public int batchCategory(List<Long> ids, String category) {
        List<Long> unique = uniqueIds(ids);
        if (unique.isEmpty()) {
            throw BusinessException.badRequest("ids_required", "请至少选择一条记录");
        }
        String normalized = categoryOrDefault(category, "");
        int updated = applicationMapper.update(null, Wrappers.<ApplicationRecord>lambdaUpdate()
                .in(ApplicationRecord::getId, unique)
                .set(ApplicationRecord::getCategory, normalized));
        applicationCache.invalidate();
        return updated;
    }

    public List<String> companySuggestions(String search) {
        QueryWrapper<ApplicationRecord> query = new QueryWrapper<>();
        query.select("DISTINCT company_name");
        if (search != null && !search.isBlank()) {
            query.apply("LOWER(company_name) LIKE {0}", "%" + search.trim().toLowerCase() + "%");
        }
        query.orderByAsc("company_name").last("LIMIT 20");
        List<Object> values = applicationMapper.selectObjs(query);
        List<String> names = new ArrayList<>();
        for (Object value : values) {
            if (value != null) {
                names.add(String.valueOf(value));
            }
        }
        return names;
    }

    public HistoryResponse history(Long id) {
        ApplicationRecord record = applicationMapper.selectById(id);
        if (record == null) {
            throw BusinessException.notFound("not_found", "投递记录不存在");
        }
        return historyService.history(record);
    }

    @Transactional
    public List<ApplicationChangeView> appendHistory(Long id, String status, String changedAt) {
        ApplicationRecord record = applicationMapper.selectById(id);
        if (record == null) {
            throw BusinessException.notFound("not_found", "投递记录不存在");
        }
        String normalized = normalizeFilter(status);
        requireStatus(normalized);
        LocalDateTime when = LocalDateTime.now();
        if (changedAt != null && !changedAt.isBlank()) {
            try {
                when = FlexibleTimes.parse(changedAt);
            } catch (IllegalArgumentException exception) {
                throw BusinessException.badRequest("invalid_date", "日期格式不正确");
            }
        }
        historyService.recordStatusChange(id, normalized, when);
        applicationCache.invalidate();
        List<ApplicationChange> changes = historyService.reconcile(record, historyService.loadChanges(id));
        List<ApplicationChangeView> views = new ArrayList<>();
        for (ApplicationChange change : changes) {
            views.add(ApplicationChangeView.of(change));
        }
        return views;
    }

    private Integer normalizeYear(Integer year) {
        if (year == null) {
            return null;
        }
        if (year <= 0) {
            throw BusinessException.badRequest("invalid_year", "年度必须为正整数");
        }
        if (year < 2000 || year > 2100) {
            throw BusinessException.badRequest("invalid_year", "年度需在 2000-2100 之间");
        }
        return year;
    }

    private String normalizeKey(String value) {
        return value == null || value.isBlank() ? "" : value.trim().toLowerCase();
    }

    private LocalDateTime parseOptional(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return FlexibleTimes.parse(value);
        } catch (IllegalArgumentException exception) {
            throw BusinessException.badRequest("invalid_range", "日期格式不正确");
        }
    }

    private String requireCompany(String value) {
        String company = value == null ? "" : value.trim();
        if (company.isEmpty()) {
            throw BusinessException.badRequest("company_required", "请输入公司名称");
        }
        if (company.length() > 255) {
            throw BusinessException.badRequest("company_too_long", "公司名称不能超过 255 个字符");
        }
        return company;
    }

    /**
     * 链接原样保存，不自动补全 http/https；但必填。
     */
    private String requireLink(String value) {
        String link = value == null ? "" : value.trim();
        if (link.isEmpty()) {
            throw BusinessException.badRequest("link_required", "请输入投递链接");
        }
        if (link.length() > 2048) {
            throw BusinessException.badRequest("link_too_long", "投递链接不能超过 2048 个字符");
        }
        return link;
    }

    private String categoryOrDefault(String value, String fallback) {
        String normalized = normalizeFilter(value);
        if (normalized.isEmpty() || normalized.equals(fallback)) {
            if (fallback == null || fallback.isEmpty()) {
                throw BusinessException.badRequest("category_required", "请选择投递类型");
            }
            return fallback;
        }
        requireCategory(normalized);
        return normalized;
    }

    private String statusOrDefault(String value, String fallback) {
        String normalized = normalizeFilter(value);
        if (normalized.isEmpty()) {
            if (fallback == null || fallback.isEmpty()) {
                throw BusinessException.badRequest("status_required", "请选择状态");
            }
            return fallback;
        }
        requireStatus(normalized);
        return normalized;
    }

    private void requireCategory(String value) {
        if (!DomainConstants.isValidCategory(value)) {
            throw BusinessException.badRequest("invalid_category",
                    "投递类型不存在，请选择春招、秋招、实习或社招");
        }
    }

    private void requireStatus(String value) {
        if (!DomainConstants.isValidStatus(value)) {
            throw BusinessException.badRequest("invalid_status",
                    "投递状态不存在，请选择待定、初筛、笔试、面试、Offer 或淘汰");
        }
    }

    private String normalizeFilter(String value) {
        if (value == null) {
            return "";
        }
        String normalized = DomainConstants.normalize(value);
        return "all".equals(normalized) ? "" : normalized;
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private List<Long> uniqueIds(List<Long> ids) {
        Set<Long> unique = new LinkedHashSet<>();
        if (ids != null) {
            for (Long id : ids) {
                if (id != null && id > 0) {
                    unique.add(id);
                }
            }
        }
        return new ArrayList<>(unique);
    }
}
