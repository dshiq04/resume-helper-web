package com.jobtrack.system.bootstrap;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.jobtrack.system.config.AppProperties;
import com.jobtrack.system.pojo.po.SysTenant;
import com.jobtrack.system.pojo.po.SysUser;
import com.jobtrack.system.mapper.SysTenantMapper;
import com.jobtrack.system.mapper.SysUserMapper;
import com.jobtrack.system.service.PermissionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 首次启动时创建默认工作区与 admin 账号（默认密码 123456）。
 * 已存在时不会覆盖用户改过的密码。
 */
@Slf4j
@Component
@Order(10)
@RequiredArgsConstructor
public class DataInitializer implements ApplicationRunner {

    private final AppProperties appProperties;
    private final SysTenantMapper tenantMapper;
    private final SysUserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final PermissionService permissionService;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        AppProperties.Admin admin = appProperties.getAdmin();

        SysTenant tenant = tenantMapper.selectOne(Wrappers.<SysTenant>lambdaQuery()
                .eq(SysTenant::getCode, "default"));
        if (tenant == null) {
            tenant = new SysTenant();
            tenant.setCode("default");
            tenant.setName(admin.getTenantName());
            tenantMapper.insert(tenant);
            log.info("已创建默认工作区: {}", tenant.getName());
        }

        String username = admin.getUsername();
        // 已注销的同名账号不计入存在性判断（用户名唯一性为逻辑判断）。
        Long existing = userMapper.selectCount(Wrappers.<SysUser>lambdaQuery()
                .eq(SysUser::getUsername, username)
                .and(w -> w.isNull(SysUser::getStatus)
                        .or().ne(SysUser::getStatus, "CANCELLED")));
        // 权限字典：普通用户、管理员，以及独立的管理员控制台权限。
        permissionService.ensurePermission("USER", "普通用户", "默认权限");
        permissionService.ensurePermission("ADMIN", "管理员", "管理员权限");
        permissionService.ensurePermission(PermissionService.ADMIN_CONSOLE,
                "管理员控制台", "访问管理员控制台（兼容旧权限）");
        permissionService.ensurePermission(PermissionService.ADMIN_USERS,
                "用户管理", "查看与修改所有用户、投递与简历");
        permissionService.ensurePermission(PermissionService.ADMIN_ONLINE,
                "在线用户", "查看在线用户并踢出");
        permissionService.ensurePermission(PermissionService.ADMIN_PERMISSIONS,
                "权限授权", "给成员授予或收回权限");

        SysUser adminUser;
        if (existing == null || existing == 0) {
            adminUser = new SysUser();
            adminUser.setTenantId(tenant.getId());
            adminUser.setUsername(username);
            adminUser.setPasswordHash(passwordEncoder.encode(admin.getPassword()));
            adminUser.setDisplayName(admin.getDisplayName());
            adminUser.setRole("ADMIN");
            adminUser.setEnabled(true);
            userMapper.insert(adminUser);
            log.info("已创建默认账号: {} / {}", username, admin.getPassword());
        } else {
            adminUser = userMapper.selectOne(Wrappers.<SysUser>lambdaQuery()
                    .eq(SysUser::getUsername, username)
                    .and(w -> w.isNull(SysUser::getStatus)
                            .or().ne(SysUser::getStatus, "CANCELLED"))
                    .orderByAsc(SysUser::getId)
                    .last("LIMIT 1"));
        }
        // 无论账号是否刚创建，都确保 admin 拥有 ADMIN 与独立的管理员控制台权限。
        if (adminUser != null) {
            permissionService.grant(adminUser.getId(), "ADMIN");
            for (String code : PermissionService.adminConsolePermissions()) {
                permissionService.grant(adminUser.getId(), code);
            }
            if (!"ADMIN".equalsIgnoreCase(adminUser.getRole())) {
                adminUser.setRole("ADMIN");
                userMapper.updateById(adminUser);
            }
        }
    }
}
