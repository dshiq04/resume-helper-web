package com.jobtrack.system.service;

/**
 * 联系方式（手机号/邮箱）校验。
 * 已注销账号占用的手机号/邮箱会被过滤，允许新用户重新注册。
 */
public interface ContactService {

    /**
     * 检查手机号或邮箱（OR 条件）是否已被其他未注销的账号占用；
     * 被占用时抛出 409 conflict。selfUserId 传入当前用户 ID 以排除自己，可为 null。
     */
    void ensureAvailable(Long selfUserId, String phone, String email);
}
