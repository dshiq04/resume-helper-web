package com.jobtrack.common.constant;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;

/**
 * 业务枚举常量。数据库与 API 统一使用英文小写值，界面负责翻译。
 */
public final class DomainConstants {

    public static final String CATEGORY_SPRING = "spring";
    public static final String CATEGORY_AUTUMN = "autumn";
    public static final String CATEGORY_INTERNSHIP = "internship";
    public static final String CATEGORY_SOCIAL = "social";

    public static final String STATUS_PENDING = "pending";
    public static final String STATUS_SCREENING = "screening";
    public static final String STATUS_WRITTEN = "written";
    public static final String STATUS_INTERVIEW = "interview";
    public static final String STATUS_OFFER = "offer";
    public static final String STATUS_REJECTED = "rejected";

    public static final List<String> CATEGORIES = List.of(
            CATEGORY_SPRING, CATEGORY_AUTUMN, CATEGORY_INTERNSHIP, CATEGORY_SOCIAL);

    /** 供 Spring Validation 使用的投递类型匹配表达式。 */
    public static final String CATEGORY_PATTERN = "(?i)^(spring|autumn|internship|social)$";

    public static final List<String> STATUSES = List.of(
            STATUS_PENDING, STATUS_SCREENING, STATUS_WRITTEN,
            STATUS_INTERVIEW, STATUS_OFFER, STATUS_REJECTED);

    /** 转化漏斗使用的正向阶段。 */
    public static final List<String> STAGE_ORDER = List.of(
            STATUS_SCREENING, STATUS_WRITTEN, STATUS_INTERVIEW, STATUS_OFFER);

    /** 详情时间线展示顺序，Offer 与淘汰为并列终态。 */
    public static final List<String> TIMELINE_ORDER = List.of(
            STATUS_PENDING, STATUS_SCREENING, STATUS_WRITTEN,
            STATUS_INTERVIEW, STATUS_OFFER, STATUS_REJECTED);

    private DomainConstants() {
    }

    public static boolean isValidCategory(String value) {
        return CATEGORIES.contains(value);
    }

    public static boolean isValidStatus(String value) {
        return STATUSES.contains(value);
    }

    public static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    /**
     * 按服务器时间推断招聘季：8-11 月秋招，2-5 月春招，其余月份默认实习。
     */
    public static String defaultCategoryForTime(LocalDateTime time) {
        int month = time.getMonthValue();
        if (month >= 8 && month <= 11) {
            return CATEGORY_AUTUMN;
        }
        if (month >= 2 && month <= 5) {
            return CATEGORY_SPRING;
        }
        return CATEGORY_INTERNSHIP;
    }

    /**
     * 到达某个状态前必须经过的阶段，用于补全时间线。
     */
    public static List<String> stageChainForStatus(String status) {
        if (STATUS_PENDING.equals(status)) {
            return List.of(STATUS_PENDING);
        }
        if (STATUS_REJECTED.equals(status)) {
            return List.of(STATUS_PENDING, STATUS_SCREENING, STATUS_WRITTEN,
                    STATUS_INTERVIEW, STATUS_REJECTED);
        }
        int index = STAGE_ORDER.indexOf(status);
        if (index < 0) {
            return List.of(STATUS_SCREENING);
        }
        java.util.ArrayList<String> chain = new java.util.ArrayList<>();
        chain.add(STATUS_PENDING);
        chain.addAll(STAGE_ORDER.subList(0, index + 1));
        return chain;
    }
}
