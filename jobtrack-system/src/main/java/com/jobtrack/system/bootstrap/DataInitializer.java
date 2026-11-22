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
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 首次启动时创建默认工作区与 admin 账号（默认密码 123456）。
 * 已存在时不会覆盖用户改过的密码。
 */
@Slf4j
@Component
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
        if (existing == null || existing == 0) {
            SysUser user = new SysUser();
            user.setTenantId(tenant.getId());
            user.setUsername(username);
            user.setPasswordHash(passwordEncoder.encode(admin.getPassword()));
            user.setDisplayName(admin.getDisplayName());
            user.setRole("ADMIN");
            user.setEnabled(true);
            userMapper.insert(user);
            // 管理员权限写入用户权限对应表。
            permissionService.grant(user.getId(), "ADMIN");
            log.info("已创建默认账号: {} / {}", username, admin.getPassword());
        }
    }
}
