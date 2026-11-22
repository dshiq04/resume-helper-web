package com.jobtrack.system.service;

import com.jobtrack.system.pojo.po.SysPermission;

import java.util.List;

/**
 * 用户权限：权限字典 + 用户权限对应表。
 * 登录时从这里查询权限并写入 Redis，用户表中的 role 仅作为兼容字段保留。
 */
public interface PermissionService {

    /** 管理员控制台兼容权限：拥有它可访问全部管理员接口。 */
    String ADMIN_CONSOLE = "ADMIN_CONSOLE";
    /** 用户管理权限：查看/修改所有用户、投递与简历。 */
    String ADMIN_USERS = "ADMIN_USERS";
    /** 在线用户权限：查看在线用户并踢出。 */
    String ADMIN_ONLINE = "ADMIN_ONLINE";
    /** 权限授权权限：给成员授予或收回权限。 */
    String ADMIN_PERMISSIONS = "ADMIN_PERMISSIONS";

    /** 所有管理员控制台权限编码。 */
    static List<String> adminConsolePermissions() {
        return List.of(ADMIN_CONSOLE, ADMIN_USERS, ADMIN_ONLINE, ADMIN_PERMISSIONS);
    }

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

    /** 根据权限列表推导角色：只有 ADMIN 是超级管理员，细分控制台权限仍保持 USER 角色。 */
    static String roleOf(List<String> permissions) {
        if (permissions != null && permissions.stream().anyMatch("ADMIN"::equalsIgnoreCase)) {
            return "ADMIN";
        }
        return "USER";
    }
}
