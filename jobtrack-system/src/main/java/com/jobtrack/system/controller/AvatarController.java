package com.jobtrack.system.controller;

import com.jobtrack.system.service.AvatarService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;

/**
 * 头像读取接口：无需登录，便于 &lt;img&gt; 直接展示。
 * 对象存储在 S3 或本地时都可用。
 */
@RestController
@RequestMapping("/api/avatar")
@RequiredArgsConstructor
public class AvatarController {

    private final AvatarService avatarService;

    @GetMapping("/{userId}")
    public ResponseEntity<byte[]> avatar(@PathVariable Long userId) {
        AvatarService.AvatarData data = avatarService.read(userId);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(data.contentType()))
                .cacheControl(CacheControl.maxAge(Duration.ofMinutes(10)).cachePublic())
                .body(data.data());
    }
}
