package com.jobtrack.application.cache;

import com.jobtrack.application.config.CacheConfig;
import com.jobtrack.application.pojo.po.ApplicationChange;
import com.jobtrack.application.pojo.po.ApplicationRecord;
import com.jobtrack.application.pojo.vo.DailyStatsVO;
import com.jobtrack.application.pojo.vo.FunnelVO;
import com.jobtrack.application.pojo.vo.ResumeListVO;
import com.jobtrack.application.pojo.vo.ResumeVO;
import com.jobtrack.application.pojo.vo.StatsVO;
import com.jobtrack.application.pojo.vo.TrashItemVO;
import com.jobtrack.application.pojo.vo.TrashListVO;
import com.jobtrack.application.pojo.vo.YearListVO;
import com.jobtrack.common.api.PageResult;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * 验证写入 Redis 的缓存值能够完整序列化 / 反序列化。
 */
class RedisCacheSerializerTest {

    private final GenericJackson2JsonRedisSerializer serializer = CacheConfig.redisSerializer();

    @Test
    void roundTripsApplicationPage() {
        ApplicationRecord record = new ApplicationRecord();
        record.setId(1L);
        record.setCompanyName("示例公司");
        record.setStatus("screening");
        record.setApplyYear(2026);
        record.setAppliedAt(LocalDateTime.of(2026, 11, 25, 9, 0));

        PageResult<ApplicationRecord> restored = (PageResult<ApplicationRecord>) roundTrip(
                PageResult.of(List.of(record), 1, 1, 10));
        assertEquals(1, restored.getTotal());
        assertEquals("示例公司", restored.getItems().get(0).getCompanyName());
        assertEquals(LocalDateTime.of(2026, 11, 25, 9, 0),
                restored.getItems().get(0).getAppliedAt());
    }

    @Test
    void roundTripsStatsAndFunnel() {
        StatsVO stats = new StatsVO();
        stats.setTotal(2);
        stats.setResumes(1);
        stats.setByCategory(new LinkedHashMap<>(Map.of("autumn", 2L)));
        stats.setByStatus(new LinkedHashMap<>(Map.of("screening", 2L)));
        stats.setByCategoryStatus(new LinkedHashMap<>(Map.of("autumn",
                new LinkedHashMap<>(Map.of("screening", 2L)))));
        stats.setByYear(new LinkedHashMap<>(Map.of("2026", 2L)));
        StatsVO.StuckScreening stuck = new StatsVO.StuckScreening();
        stuck.setCount(2);
        stuck.setAverageDays(3);
        stuck.setMaxDays(5);
        stats.setStuckScreening(stuck);

        StatsVO restoredStats = (StatsVO) roundTrip(stats);
        assertEquals(2, restoredStats.getTotal());
        assertEquals(3, restoredStats.getStuckScreening().getAverageDays());

        FunnelVO funnel = new FunnelVO();
        funnel.setTotal(2);
        funnel.setStages(List.of(new FunnelVO.FunnelStage("applied", 2, 100)));
        FunnelVO.CompanyFunnel company = new FunnelVO.CompanyFunnel();
        company.setCompany("示例公司");
        company.setTotal(2);
        funnel.setCompanies(List.of(company));

        FunnelVO restoredFunnel = (FunnelVO) roundTrip(funnel);
        assertEquals("applied", restoredFunnel.getStages().get(0).getKey());
        assertEquals("示例公司", restoredFunnel.getCompanies().get(0).getCompany());
    }

    @Test
    void roundTripsDailyResumeTrashAndYears() {
        DailyStatsVO.DailyStat daily = new DailyStatsVO.DailyStat("2026-11-25", "autumn", 2);
        DailyStatsVO restoredDaily = (DailyStatsVO) roundTrip(new DailyStatsVO(List.of(daily)));
        assertEquals(2, restoredDaily.getDaily().get(0).getCount());

        ResumeVO resume = new ResumeVO();
        resume.setId(3L);
        resume.setName("Java 简历");
        resume.setCreatedAt(LocalDateTime.of(2026, 11, 25, 9, 0));
        ResumeListVO restoredResumes = (ResumeListVO) roundTrip(
                new ResumeListVO(List.of(resume), 1));
        assertEquals("Java 简历", restoredResumes.getItems().get(0).getName());

        ApplicationChange change = new ApplicationChange();
        change.setId(4L);
        change.setStatus("screening");
        change.setChangedAt(LocalDateTime.of(2026, 11, 25, 9, 0));
        TrashItemVO item = new TrashItemVO();
        item.setId(5L);
        item.setCompanyName("示例公司");
        item.setHistory(List.of(change));
        TrashListVO restoredTrash = (TrashListVO) roundTrip(new TrashListVO(List.of(item), 1));
        assertEquals("screening", restoredTrash.getItems().get(0).getHistory().get(0).getStatus());

        YearListVO restoredYears = (YearListVO) roundTrip(new YearListVO(List.of(2026)));
        assertEquals(2026, restoredYears.getItems().get(0));
    }

    private Object roundTrip(Object value) {
        byte[] bytes = serializer.serialize(value);
        assertNotNull(bytes);
        Object restored = serializer.deserialize(bytes);
        assertNotNull(restored);
        assertInstanceOf(value.getClass(), restored);
        return restored;
    }
}
