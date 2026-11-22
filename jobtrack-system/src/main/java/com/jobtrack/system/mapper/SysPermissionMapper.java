package com.jobtrack.system.mapper;

import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jobtrack.system.pojo.po.SysPermission;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface SysPermissionMapper extends BaseMapper<SysPermission> {

    /** 权限字典全局共享，绕过租户拦截器。 */
    @InterceptorIgnore(tenantLine = "true")
    @Select("SELECT * FROM sys_permission WHERE code = #{code} LIMIT 1")
    SysPermission selectByCodeIgnoreTenant(String code);
}
