package com.jobtrack.system.service;

import com.jobtrack.system.entity.AccessTokenLog;
import com.jobtrack.system.mapper.AccessTokenLogMapper;
import com.jobtrack.system.messaging.TokenLogMessage;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 授权码日志落库，供同步降级与 MQ 消费者共用。
 */
@Service
@RequiredArgsConstructor
public class AccessTokenLogWriter {

    private final AccessTokenLogMapper logMapper;

    public void save(TokenLogMessage message) {
        AccessTokenLog entry = new AccessTokenLog();
        entry.setTenantId(message.tenantId());
        entry.setUserId(message.userId());
        entry.setTokenId(message.tokenId());
        entry.setTokenName(message.tokenName());
        entry.setCodePrefix(message.codePrefix());
        entry.setScope(message.scope());
        entry.setResources(message.resources());
        entry.setResult(message.result());
        entry.setIp(message.ip());
        entry.setUserAgent(message.userAgent());
        logMapper.insert(entry);
    }
}
