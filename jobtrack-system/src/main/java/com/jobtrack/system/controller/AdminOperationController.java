package com.jobtrack.system.controller;

import com.jobtrack.system.pojo.vo.UserOperationPageVO;
import com.jobtrack.system.service.UserOperationLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * 管理员 · 用户操作记录。支持按用户、类型与时间范围筛选。
 */
@RestController
@RequestMapping("/api/admin/operations")
@RequiredArgsConstructor
public class AdminOperationController {

    private final UserOperationLogService operationLogService;

    @GetMapping
    public UserOperationPageVO list(
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        return operationLogService.page(userId, type, parse(from, false), parse(to, true),
                keyword, page, pageSize);
    }

    /** 兼容日期与日期时间两种参数，日期结束值自动补到 23:59:59。 */
    private LocalDateTime parse(String value, boolean endOfDay) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String text = value.trim();
        try {
            if (text.length() == 10) {
                LocalDate date = LocalDate.parse(text);
                return endOfDay ? date.atTime(LocalTime.MAX) : date.atStartOfDay();
            }
            return LocalDateTime.parse(text);
        } catch (Exception exception) {
            return null;
        }
    }
}
