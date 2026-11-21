package com.jobtrack.system.service;

import com.jobtrack.system.pojo.AccessTokenCreateRequest;
import com.jobtrack.system.pojo.AccessTokenLogPage;
import com.jobtrack.system.pojo.AccessTokenView;
import com.jobtrack.system.pojo.TokenValidationResponse;
import com.jobtrack.system.pojo.AccessToken;
import com.jobtrack.system.pojo.SysUser;

import java.util.List;

/**
 * 授权码管理：用于浏览器扩展等外部调用方。
 */
public interface AccessTokenService {

    List<AccessTokenView> list(Long userId);

    AccessTokenView create(Long tenantId, Long userId, AccessTokenCreateRequest request);

    void delete(Long userId, Long id);

    AccessTokenView setEnabled(Long userId, Long id, boolean enabled);

    TokenValidationResponse validate(String rawCode, String ip, String userAgent);

    TokenAuth authenticate(String rawCode);

    AccessTokenLogPage logs(Long userId, int page, int pageSize, String name, String result);

    void warmCache();

    record TokenAuth(AccessToken token, SysUser user) {
    }
}
