package com.jobtrack.system.service;

import com.jobtrack.system.pojo.dto.AccessTokenCreateDTO;
import com.jobtrack.system.pojo.vo.AccessTokenLogPageVO;
import com.jobtrack.system.pojo.vo.AccessTokenVO;
import com.jobtrack.system.pojo.vo.TokenValidationVO;
import com.jobtrack.system.pojo.po.AccessToken;
import com.jobtrack.system.pojo.po.SysUser;

import java.util.List;

/**
 * 授权码管理：用于浏览器扩展等外部调用方。
 */
public interface AccessTokenService {

    /** 授权码可授权的数据范围，与 ApiResource 的鉴权路径一一对应。 */
    List<String> ALL_RESOURCES = List.of("applications", "resumes", "statistics", "profile");

    List<AccessTokenVO> list(Long userId);

    /** 删除指定用户授权码在 Redis 中的缓存，用于级联删除用户。 */
    void evictUserCache(Long userId);

    AccessTokenVO create(Long tenantId, Long userId, AccessTokenCreateDTO request);

    void delete(Long userId, Long id);

    AccessTokenVO setEnabled(Long userId, Long id, boolean enabled);

    TokenValidationVO validate(String rawCode, String ip, String userAgent);

    TokenAuth authenticate(String rawCode);

    /** 解析授权码保存的数据范围（已过滤非法值，空值回退为全部范围）。 */
    List<String> resources(AccessToken token);

    AccessTokenLogPageVO logs(Long userId, int page, int pageSize, String name, String result);

    void warmCache();

    record TokenAuth(AccessToken token, SysUser user) {
    }
}
