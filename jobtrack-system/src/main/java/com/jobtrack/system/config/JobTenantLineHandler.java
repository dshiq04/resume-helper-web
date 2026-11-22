package com.jobtrack.system.config;

import com.baomidou.mybatisplus.extension.plugins.handler.TenantLineHandler;
import com.jobtrack.common.context.TenantContext;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.LongValue;

/**
 * 多租户规则：所有业务表自动追加 tenant_id；账号与租户表本身不参与过滤，
 * 否则登录阶段无法在“还不知道租户”的情况下查询用户。
 */
public class JobTenantLineHandler implements TenantLineHandler {

    @Override
    public Expression getTenantId() {
        return new LongValue(TenantContext.require());
    }

    @Override
    public String getTenantIdColumn() {
        return "tenant_id";
    }

    @Override
    public boolean ignoreTable(String tableName) {
        return "sys_user".equalsIgnoreCase(tableName)
                || "sys_tenant".equalsIgnoreCase(tableName)
                // 权限表为全局字典与用户映射，无 tenant_id 列；
                // 注册/登录阶段查询权限时也没有租户上下文。
                || "sys_permission".equalsIgnoreCase(tableName)
                || "sys_user_permission".equalsIgnoreCase(tableName)
                // 授权码校验发生在登录之前，无法依赖 TenantContext。
                || "access_token".equalsIgnoreCase(tableName)
                || "access_token_log".equalsIgnoreCase(tableName)
                // 封禁校验同样发生在登录之前，按 user_id 查询封禁记录。
                || "sys_user_ban".equalsIgnoreCase(tableName);
    }
}
