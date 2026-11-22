package com.jobtrack.system.service;

import com.jobtrack.system.pojo.po.SysUser;

import java.time.LocalDateTime;

/**
 * 封禁用户集合。
 *
 * <p>Redis 中存在封禁集合 {@code jobtrack:ban:users}（ZSet，score 为到期时间，
 * 永久封禁使用 Long.MAX_VALUE），登录、JWT 与授权码校验都先检查这里。
 * Redis 未启用或临时不可用时退回进程内集合，重启后由数据库封禁状态重新同步。</p>
 */
public interface BanService {

    /** 仅按封禁集合判断（供每个请求的 JWT 过滤器使用）。 */
    boolean isBanned(Long userId);

    /** 同时参考数据库中的账号状态与封禁到期时间。 */
    boolean isBanned(SysUser user);

    /** 封禁提示文案；未封禁返回 null。 */
    String message(Long userId);

    /** 根据数据库字段生成封禁提示文案。 */
    String message(SysUser user);

    /**
     * 写入 / 移除封禁集合（只改缓存，不改数据库）。
     *
     * @param banned true 表示封禁，false 表示解除
     * @param until  暂时封禁的到期时间；永久封禁传 null
     */
    void apply(Long userId, boolean banned, LocalDateTime until);

    /** 启动时从数据库同步封禁集合。 */
    void warmCache();
}
