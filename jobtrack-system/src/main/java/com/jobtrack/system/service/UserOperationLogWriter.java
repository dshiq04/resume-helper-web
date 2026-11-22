package com.jobtrack.system.service;

import com.jobtrack.system.mapper.UserOperationLogMapper;
import com.jobtrack.system.messaging.UserOperationMessage;
import com.jobtrack.system.pojo.po.UserOperationLog;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 用户操作记录落库，供 RabbitMQ 消费与 Broker 不可用时的同步降级共用。
 */
@Service
@RequiredArgsConstructor
public class UserOperationLogWriter {

    private final UserOperationLogMapper operationMapper;

    public void save(UserOperationMessage message) {
        if (message == null || message.userId() == null) {
            return;
        }
        UserOperationLog entry = new UserOperationLog();
        entry.setTenantId(message.tenantId());
        entry.setUserId(message.userId());
        entry.setUsername(message.username());
        entry.setDisplayName(message.displayName());
        entry.setOperationType(message.operationType());
        entry.setAction(message.action());
        entry.setDescription(message.description());
        entry.setMethod(message.method());
        entry.setPath(message.path());
        entry.setIp(message.ip());
        entry.setUserAgent(message.userAgent());
        entry.setCreatedAt(message.createdAt());
        operationMapper.insert(entry);
    }
}
