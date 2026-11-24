package com.jobtrack.system.controller;

import com.jobtrack.system.service.AvatarService;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;

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
    public void avatar(@PathVariable Long userId, HttpServletResponse response) throws IOException {
        AvatarService.AvatarData data = avatarService.read(userId);
        response.setContentType(data.contentType());
        response.setHeader(HttpHeaders.CACHE_CONTROL, "max-age=600, public");
        response.setContentLength(data.data().length);
        response.getOutputStream().write(data.data());
    }
}
