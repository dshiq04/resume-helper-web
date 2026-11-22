package com.jobtrack.system.mapper;

import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jobtrack.system.pojo.SysUserPermission;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface SysUserPermissionMapper extends BaseMapper<SysUserPermission> {

    /** 查询某用户的全部权限编码，登录时使用。 */
    @InterceptorIgnore(tenantLine = "true")
    @Select("SELECT p.code FROM sys_user_permission up "
            + "JOIN sys_permission p ON p.id = up.permission_id "
            + "WHERE up.user_id = #{userId} ORDER BY p.id")
    List<String> selectPermissionCodes(@Param("userId") Long userId);

    /** 用户是否拥有指定权限。 */
    @InterceptorIgnore(tenantLine = "true")
    @Select("SELECT COUNT(*) FROM sys_user_permission up "
            + "JOIN sys_permission p ON p.id = up.permission_id "
            + "WHERE up.user_id = #{userId} AND p.code = #{code}")
    long countByUserAndCode(@Param("userId") Long userId, @Param("code") String code);
}
