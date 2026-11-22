package com.jobtrack.system.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.jobtrack.common.exception.BusinessException;
import com.jobtrack.system.mapper.SysPermissionMapper;
import com.jobtrack.system.mapper.SysUserPermissionMapper;
import com.jobtrack.system.pojo.SysPermission;
import com.jobtrack.system.pojo.SysUserPermission;
import com.jobtrack.system.service.PermissionService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class PermissionServiceImpl implements PermissionService {

    private final SysPermissionMapper permissionMapper;
    private final SysUserPermissionMapper userPermissionMapper;

    public List<String> permissionsOf(Long userId) {
        if (userId == null) {
            return List.of();
        }
        List<String> codes = userPermissionMapper.selectPermissionCodes(userId);
        return codes == null ? List.of() : codes;
    }

    @Transactional
    public void grant(Long userId, String code) {
        SysPermission permission = permissionMapper.selectByCodeIgnoreTenant(code);
        if (permission == null) {
            throw BusinessException.internal("permission_missing", "权限不存在: " + code);
        }
        if (userPermissionMapper.countByUserAndCode(userId, code) > 0) {
            return;
        }
        SysUserPermission relation = new SysUserPermission();
        relation.setUserId(userId);
        relation.setPermissionId(permission.getId());
        userPermissionMapper.insert(relation);
    }

    public boolean has(Long userId, String code) {
        return userId != null && userPermissionMapper.countByUserAndCode(userId, code) > 0;
    }

    public boolean hasElevated(Long userId) {
        List<String> codes = permissionsOf(userId);
        return codes.stream().anyMatch(code -> !"USER".equalsIgnoreCase(code));
    }

    /** 供启动迁移使用：确保权限字典存在。 */
    public SysPermission ensurePermission(String code, String name, String description) {
        SysPermission permission = permissionMapper.selectByCodeIgnoreTenant(code);
        if (permission != null) {
            return permission;
        }
        SysPermission created = new SysPermission();
        created.setCode(code);
        created.setName(name);
        created.setDescription(description);
        permissionMapper.insert(created);
        return created;
    }

    /** 用户当前权限数量，迁移判断用。 */
    public long countUserPermissions(Long userId) {
        return userPermissionMapper.selectCount(Wrappers.<SysUserPermission>lambdaQuery()
                .eq(SysUserPermission::getUserId, userId));
    }
}
