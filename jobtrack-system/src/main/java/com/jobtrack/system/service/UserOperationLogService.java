package com.jobtrack.system.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.jobtrack.system.mapper.UserOperationLogMapper;
import com.jobtrack.system.messaging.AsyncTaskPublisher;
import com.jobtrack.system.messaging.UserOperationMessage;
import com.jobtrack.system.pojo.po.UserOperationLog;
import com.jobtrack.system.pojo.vo.UserActivityVO;
import com.jobtrack.system.pojo.vo.UserOperationPageVO;
import com.jobtrack.system.pojo.vo.UserOperationVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 用户操作记录：AOP 捕获后发布到 RabbitMQ，消费者异步落库；
 * 管理端按用户、类型与时间范围分页查询。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserOperationLogService {

    private final UserOperationLogMapper operationMapper;
    private final AsyncTaskPublisher publisher;
    private final UserOperationLogWriter writer;

    /** 记录一次操作。Broker 不可用时自动降级为同步写库。 */
    public void record(UserOperationMessage message) {
        if (message == null || message.userId() == null) {
            return;
        }
        if (!publisher.publishUserOperation(message)) {
            writer.save(message);
        }
    }

    public UserOperationPageVO page(Long userId, String type, LocalDateTime from,
                                    LocalDateTime to, String keyword,
                                    int page, int pageSize) {
        int current = Math.max(1, page);
        int size = Math.min(100, Math.max(1, pageSize));
        String normalizedType = clean(type).toUpperCase(Locale.ROOT);
        String search = clean(keyword);
        var query = Wrappers.<UserOperationLog>lambdaQuery();
        if (userId != null) {
            query.eq(UserOperationLog::getUserId, userId);
        }
        if (!normalizedType.isEmpty() && !"ALL".equals(normalizedType)) {
            query.eq(UserOperationLog::getOperationType, normalizedType);
        }
        if (from != null) {
            query.ge(UserOperationLog::getCreatedAt, from);
        }
        if (to != null) {
            query.le(UserOperationLog::getCreatedAt, to);
        }
        if (!search.isEmpty()) {
            query.and(w -> w.like(UserOperationLog::getUsername, search)
                    .or().like(UserOperationLog::getDisplayName, search)
                    .or().like(UserOperationLog::getDescription, search)
                    .or().like(UserOperationLog::getPath, search));
        }
        query.orderByDesc(UserOperationLog::getCreatedAt)
                .orderByDesc(UserOperationLog::getId);
        Page<UserOperationLog> result = operationMapper.selectPage(new Page<>(current, size), query);
        List<UserOperationVO> items = result.getRecords().stream().map(this::view).toList();
        return new UserOperationPageVO(items, result.getTotal(), current, size);
    }

    /** 批量查询最后一次操作与最后一次登录时间。 */
    public Map<Long, UserActivityVO> lastActivity(List<Long> userIds) {
        Map<Long, UserActivityVO> result = new HashMap<>();
        if (userIds == null || userIds.isEmpty()) {
            return result;
        }
        for (UserActivityVO activity : operationMapper.selectActivity(userIds)) {
            if (activity != null && activity.getUserId() != null) {
                result.put(activity.getUserId(), activity);
            }
        }
        return result;
    }

    public UserActivityVO activityOf(Long userId) {
        if (userId == null) {
            return null;
        }
        return lastActivity(List.of(userId)).get(userId);
    }

    private UserOperationVO view(UserOperationLog entry) {
        UserOperationVO view = new UserOperationVO();
        BeanUtils.copyProperties(entry, view);
        return view;
    }

    private String clean(String value) {
        return value == null ? "" : value.trim();
    }
}
