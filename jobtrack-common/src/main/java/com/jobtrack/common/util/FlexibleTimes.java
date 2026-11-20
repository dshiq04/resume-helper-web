package com.jobtrack.common.util;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

/**
 * 前端可能传来 ISO-8601、日期时间或纯日期，这里统一解析为本地时间。
 */
public final class FlexibleTimes {

    private static final DateTimeFormatter SPACE_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private FlexibleTimes() {
    }

    public static LocalDateTime parse(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("empty date");
        }
        String value = raw.trim();
        try {
            return OffsetDateTime.parse(value).atZoneSameInstant(ZoneId.systemDefault())
                    .toLocalDateTime();
        } catch (DateTimeParseException ignored) {
            // Try the remaining formats.
        }
        try {
            return LocalDateTime.parse(value);
        } catch (DateTimeParseException ignored) {
            // Try the space separated format.
        }
        try {
            return LocalDateTime.parse(value, SPACE_FORMAT);
        } catch (DateTimeParseException ignored) {
            // Try a plain date.
        }
        try {
            return LocalDate.parse(value).atStartOfDay();
        } catch (DateTimeParseException exception) {
            throw new IllegalArgumentException("Unsupported date: " + raw, exception);
        }
    }
}
