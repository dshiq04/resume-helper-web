package com.jobtrack.system.service;

/**
 * 头像地址与内容读取。
 */
public interface AvatarService {

    String url(Long userId, String key);

    AvatarData read(Long userId);

    record AvatarData(byte[] data, String contentType) {
    }
}
