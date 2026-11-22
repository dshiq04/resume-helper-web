package com.jobtrack.system.mapper;

import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jobtrack.system.pojo.UserProfile;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface UserProfileMapper extends BaseMapper<UserProfile> {

    /** 头像读取接口可能未登录，需要绕过租户拦截器。 */
    @InterceptorIgnore(tenantLine = "true")
    @Select("SELECT * FROM user_profile WHERE user_id = #{userId} LIMIT 1")
    UserProfile selectByUserIdIgnoreTenant(@Param("userId") Long userId);

    /** 注册时检查手机号/邮箱是否已被其他账号使用。 */
    @InterceptorIgnore(tenantLine = "true")
    @Select("SELECT tenant_id, user_id, phone, email FROM user_profile WHERE phone IS NOT NULL OR email IS NOT NULL")
    List<UserProfile> selectContactsIgnoreTenant();

    /** 只取未注销账号的手机号/邮箱：已注销账号的联系方式允许被重新注册使用。 */
    @InterceptorIgnore(tenantLine = "true")
    @Select("SELECT up.tenant_id, up.user_id, up.phone, up.email FROM user_profile up "
            + "JOIN sys_user u ON u.id = up.user_id "
            + "WHERE (up.phone IS NOT NULL OR up.email IS NOT NULL) "
            + "AND (u.status IS NULL OR u.status <> 'CANCELLED')")
    List<UserProfile> selectActiveContactsIgnoreTenant();
}
