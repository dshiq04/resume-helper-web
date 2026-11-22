package com.jobtrack.system.service;

import com.jobtrack.system.pojo.po.SysUser;
import com.jobtrack.system.pojo.po.SysUserBan;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 封禁状态与封禁记录。
 *
 * <p>Redis 中维护封禁集合 {@code jobtrack:ban:users}（ZSet，score 为到期时间，
 * 永久封禁使用 Long.MAX_VALUE），用于每个请求的快速拒绝；封禁状态、到期时间与原因
 * 以 {@code sys_user_ban} 记录表为准。</p>
 */
public interface BanService {

    /** 当前有效的封禁记录；未封禁返回 null。 */
    SysUserBan activeBan(Long userId);

    /** 全部当前有效的封禁记录。 */
    List<SysUserBan> activeBans();

    /** 仅按封禁集合判断（供每个请求的 JWT 过滤器使用）。 */
    boolean isBanned(Long userId);

    /** 同时参考封禁记录与 Redis 集合。 */
    boolean isBanned(SysUser user);

    /** 封禁提示文案；未封禁返回 null。 */
    String message(Long userId);

    /** 封禁提示文案；未封禁返回 null。 */
    String message(SysUser user);

    /** 新增一条封禁记录并更新本地封禁集合。 */
    SysUserBan ban(Long userId, Long tenantId, Long operatorId,
                   String banType, LocalDateTime banUntil, String reason);

    /** 解除当前用户的有效封禁记录并移出封禁集合。 */
    void unban(Long userId);

    /**
     * 写入 / 移除封禁集合（只改缓存，不改数据库）。
     * 供 RabbitMQ 消费端同步其他实例使用。
     */
    void apply(Long userId, boolean banned, LocalDateTime until);

    /** 启动时从封禁记录表同步封禁集合。 */
    void warmCache();
}
