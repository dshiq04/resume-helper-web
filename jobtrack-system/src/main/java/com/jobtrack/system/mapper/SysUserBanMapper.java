package com.jobtrack.system.mapper;

import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jobtrack.system.pojo.po.SysUserBan;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 封禁记录。登录与授权码校验发生在租户上下文建立之前，
 * 因此这里统一绕过租户拦截器，按 user_id 查询。
 */
@Mapper
public interface SysUserBanMapper extends BaseMapper<SysUserBan> {

    /** 当前有效的封禁记录：未手动解除，且永久或尚未到期。 */
    @InterceptorIgnore(tenantLine = "true")
    @Select("SELECT * FROM sys_user_ban "
            + "WHERE user_id = #{userId} AND unbanned_at IS NULL "
            + "AND (ban_until IS NULL OR ban_until > #{now}) "
            + "ORDER BY banned_at DESC, id DESC LIMIT 1")
    SysUserBan selectActiveByUserId(@Param("userId") Long userId,
                                    @Param("now") LocalDateTime now);

    /** 全部当前有效的封禁记录，用于启动时同步 Redis 集合。 */
    @InterceptorIgnore(tenantLine = "true")
    @Select("SELECT * FROM sys_user_ban "
            + "WHERE unbanned_at IS NULL "
            + "AND (ban_until IS NULL OR ban_until > #{now})")
    List<SysUserBan> selectActiveBans(@Param("now") LocalDateTime now);

    /** 手动解除某个用户当前有效的封禁记录。 */
    @InterceptorIgnore(tenantLine = "true")
    @Update("UPDATE sys_user_ban SET unbanned_at = #{unbannedAt} "
            + "WHERE user_id = #{userId} AND unbanned_at IS NULL")
    int revokeActive(@Param("userId") Long userId,
                     @Param("unbannedAt") LocalDateTime unbannedAt);
}
