package com.jobtrack.system.mapper;

import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jobtrack.system.pojo.po.UserAgreement;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;

@Mapper
public interface UserAgreementMapper extends BaseMapper<UserAgreement> {

    /**
     * 注册发生在登录之前，此时还没有 TenantContext，需要绕过租户拦截器。
     */
    @InterceptorIgnore(tenantLine = "true")
    @Insert("INSERT INTO user_agreement (tenant_id, user_id, accepted_at, language, "
            + "signer_name, location, timezone, signed_ip) "
            + "VALUES (#{tenantId}, #{userId}, #{acceptedAt}, #{language}, "
            + "#{signerName}, #{location}, #{timezone}, #{signedIp})")
    int insertIgnoreTenant(@Param("tenantId") Long tenantId,
                           @Param("userId") Long userId,
                           @Param("acceptedAt") LocalDateTime acceptedAt,
                           @Param("language") String language,
                           @Param("signerName") String signerName,
                           @Param("location") String location,
                           @Param("timezone") String timezone,
                           @Param("signedIp") String signedIp);

    /**
     * 管理员查看任意用户、登录补签时都需要跨租户读取。
     */
    @InterceptorIgnore(tenantLine = "true")
    @Select("SELECT * FROM user_agreement WHERE user_id = #{userId} ORDER BY id DESC LIMIT 1")
    UserAgreement selectByUserIdIgnoreTenant(@Param("userId") Long userId);

    @InterceptorIgnore(tenantLine = "true")
    @Update("UPDATE user_agreement SET accepted_at = #{acceptedAt}, language = #{language}, "
            + "signer_name = #{signerName}, location = #{location}, timezone = #{timezone}, "
            + "signed_ip = #{signedIp} WHERE user_id = #{userId}")
    int updateByUserIdIgnoreTenant(@Param("userId") Long userId,
                                   @Param("acceptedAt") LocalDateTime acceptedAt,
                                   @Param("language") String language,
                                   @Param("signerName") String signerName,
                                   @Param("location") String location,
                                   @Param("timezone") String timezone,
                                   @Param("signedIp") String signedIp);
}
