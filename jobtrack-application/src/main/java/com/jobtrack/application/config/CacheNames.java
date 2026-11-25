package com.jobtrack.application.config;

/**
 * 业务缓存名称。
 *
 * <p>投递、统计、简历与回收站的查询结果分别放在独立缓存中，
 * 写操作按需整体清理，下一次查询时重新读库并回填缓存。</p>
 */
public final class CacheNames {

    /** 投递列表（分页、筛选条件由缓存键区分）。 */
    public static final String APPLICATIONS = "applications";

    /** 投递年度选项。 */
    public static final String APPLICATION_YEARS = "applicationYears";

    /** 统计分析概览。 */
    public static final String STATS = "stats";

    /** 按天投递趋势。 */
    public static final String DAILY_STATS = "dailyStats";

    /** 阶段转化与公司漏斗。 */
    public static final String FUNNEL = "funnel";

    /** 简历列表。 */
    public static final String RESUMES = "resumes";

    /** 回收站列表。 */
    public static final String TRASH = "trash";

    /** 所有业务缓存，用于演示数据初始化等需要全量失效的场景。 */
    public static final String[] ALL = {
            APPLICATIONS, APPLICATION_YEARS, STATS, DAILY_STATS, FUNNEL, RESUMES, TRASH
    };

    private CacheNames() {
    }
}
