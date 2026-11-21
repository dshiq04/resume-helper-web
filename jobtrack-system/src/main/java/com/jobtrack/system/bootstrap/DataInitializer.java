package com.jobtrack.system.bootstrap;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.jobtrack.system.config.AppProperties;
import com.jobtrack.system.pojo.SysTenant;
import com.jobtrack.system.pojo.SysUser;
import com.jobtrack.system.mapper.SysTenantMapper;
import com.jobtrack.system.mapper.SysUserMapper;
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
        Long existing = userMapper.selectCount(Wrappers.<SysUser>lambdaQuery()
                .eq(SysUser::getUsername, username));
        if (existing == null || existing == 0) {
            SysUser user = new SysUser();
            user.setTenantId(tenant.getId());
            user.setUsername(username);
            user.setPasswordHash(passwordEncoder.encode(admin.getPassword()));
            user.setDisplayName(admin.getDisplayName());
            user.setRole("ADMIN");
            user.setEnabled(true);
            userMapper.insert(user);
            log.info("已创建默认账号: {} / {}", username, admin.getPassword());
        }
    }
}
