package com.jobtrack.system.mapper;

import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jobtrack.system.pojo.UserAgreement;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;

@Mapper
public interface UserAgreementMapper extends BaseMapper<UserAgreement> {

    /**
     * 注册发生在登录之前，此时还没有 TenantContext，需要绕过租户拦截器。
     */
    @InterceptorIgnore(tenantLine = "true")
    @Insert("INSERT INTO user_agreement (tenant_id, user_id, accepted_at) "
            + "VALUES (#{tenantId}, #{userId}, #{acceptedAt})")
    int insertIgnoreTenant(@Param("tenantId") Long tenantId,
                           @Param("userId") Long userId,
                           @Param("acceptedAt") LocalDateTime acceptedAt);
}
