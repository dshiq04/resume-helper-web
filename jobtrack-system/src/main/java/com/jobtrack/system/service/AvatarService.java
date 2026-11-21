package com.jobtrack.system.service;

import com.jobtrack.common.exception.BusinessException;
import com.jobtrack.system.entity.UserProfile;
import com.jobtrack.system.mapper.UserProfileMapper;
import com.jobtrack.system.storage.ObjectStorageService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Locale;

/**
 * 头像地址与内容读取。
 *
 * <p>优先返回对象存储的公共地址；S3 不可用或对象不存在时，回退到后端头像接口，
 * 保证本地存储的头像也能正常显示。</p>
 */
@Service
@RequiredArgsConstructor
public class AvatarService {

    private final UserProfileMapper profileMapper;
    private final ObjectStorageService objectStorage;

    public String url(Long userId, String key) {
        if (key == null || key.isBlank()) {
            return null;
        }
        if (!objectStorage.exists(key)) {
            return null;
        }
        String publicUrl = objectStorage.publicUrl(key);
        return publicUrl != null ? publicUrl : "/api/avatar/" + userId;
    }

    public AvatarData read(Long userId) {
        UserProfile profile = profileMapper.selectByUserIdIgnoreTenant(userId);
        if (profile == null || profile.getAvatarUrl() == null || profile.getAvatarUrl().isBlank()) {
            throw BusinessException.notFound("avatar_missing", "未设置头像");
        }
        byte[] data = objectStorage.get(profile.getAvatarUrl());
        return new AvatarData(data, contentType(profile.getAvatarUrl()));
    }

    private String contentType(String key) {
        String lower = key.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".png")) {
            return "image/png";
        }
        if (lower.endsWith(".webp")) {
            return "image/webp";
        }
        if (lower.endsWith(".gif")) {
            return "image/gif";
        }
        return "image/jpeg";
    }

    public record AvatarData(byte[] data, String contentType) {
    }
}
