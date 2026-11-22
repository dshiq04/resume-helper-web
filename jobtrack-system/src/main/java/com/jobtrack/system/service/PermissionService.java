package com.jobtrack.system.service;

import com.jobtrack.system.pojo.po.SysPermission;

import java.util.List;

/**
 * 用户权限：权限字典 + 用户权限对应表。
 * 登录时从这里查询权限并写入 Redis，用户表中的 role 仅作为兼容字段保留。
 */
public interface PermissionService {

    /** 管理员控制台权限编码：拥有它才能访问 /api/admin/**。 */
    String ADMIN_CONSOLE = "ADMIN_CONSOLE";

    /** 用户拥有的全部权限编码，如 [USER] 或 [USER, ADMIN]。 */
    List<String> permissionsOf(Long userId);

    /** 权限字典中的全部权限。 */
    List<SysPermission> listPermissions();

    /** 确保权限字典中存在指定权限，返回已存在或新建的权限。 */
    SysPermission ensurePermission(String code, String name, String description);

    /** 授予用户一个权限（幂等）。 */
    void grant(Long userId, String code);

    /** 收回用户的一个权限（幂等）。 */
    void revoke(Long userId, String code);

    /** 用户是否拥有指定权限。 */
    boolean has(Long userId, String code);

    /** 用户是否拥有普通用户以外的权限。 */
    boolean hasElevated(Long userId);

    /** 根据权限列表推导角色：拥有管理员或管理员控制台权限则为 ADMIN，否则 USER。 */
    static String roleOf(List<String> permissions) {
        if (permissions != null && permissions.stream().anyMatch(code ->
                "ADMIN".equalsIgnoreCase(code) || ADMIN_CONSOLE.equalsIgnoreCase(code))) {
            return "ADMIN";
        }
        return "USER";
    }
}
