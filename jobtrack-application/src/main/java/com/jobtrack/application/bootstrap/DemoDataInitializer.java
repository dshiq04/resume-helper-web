package com.jobtrack.application.bootstrap;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.jobtrack.application.entity.ApplicationRecord;
import com.jobtrack.application.mapper.ApplicationRecordMapper;
import com.jobtrack.application.service.ApplicationHistoryService;
import com.jobtrack.common.constant.DomainConstants;
import com.jobtrack.common.context.TenantContext;
import com.jobtrack.system.entity.SysTenant;
import com.jobtrack.system.mapper.SysTenantMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 可选的演示数据。config.json 中 app.seedDemo=true 时，
 * 只在默认工作区为空的情况下写入几条示例，方便首次打开时查看界面。
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.seedDemo", havingValue = "true")
public class DemoDataInitializer implements ApplicationRunner {

    private final SysTenantMapper tenantMapper;
    private final ApplicationRecordMapper applicationMapper;
    private final ApplicationHistoryService historyService;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        SysTenant tenant = tenantMapper.selectOne(Wrappers.<SysTenant>lambdaQuery()
                .eq(SysTenant::getCode, "default"));
        if (tenant == null) {
            return;
        }
        TenantContext.set(tenant.getId());
        try {
            Long existing = applicationMapper.selectCount(null);
            if (existing != null && existing > 0) {
                return;
            }
            List<Demo> demos = List.of(
                    new Demo("字节跳动", "https://jobs.bytedance.com/position/1001", "后端开发工程师",
                            "秋招", DomainConstants.STATUS_INTERVIEW, "官网", "北京", "25-40K", "high", 2, 42, 12),
                    new Demo("小红书", "https://job.xiaohongshu.com/jobs/2002", "前端开发工程师",
                            "秋招", DomainConstants.STATUS_WRITTEN, "内推", "上海", "22-35K", "high", 1, 30, 9),
                    new Demo("美团", "https://zhaopin.meituan.com/job/3003", "数据开发",
                            "秋招", DomainConstants.STATUS_SCREENING, "官网", "北京", "20-32K", "medium", 0, 21, 21),
                    new Demo("网易", "https://hr.163.com/position/4004", "Java 开发工程师",
                            "春招", DomainConstants.STATUS_OFFER, "牛客", "杭州", "24-36K", "high", 3, 65, 40),
                    new Demo("腾讯", "https://careers.tencent.com/job/5005", "客户端开发",
                            "春招", DomainConstants.STATUS_REJECTED, "官网", "深圳", "25-40K", "medium", 2, 50, 35),
                    new Demo("米哈游", "https://jobs.mihoyo.com/position/6006", "游戏服务端开发",
                            "实习", DomainConstants.STATUS_PENDING, "内推", "上海", "300/天", "medium", 0, 8, 8),
                    new Demo("拼多多", "https://careers.pinduoduo.com/job/7007", "算法工程师",
                            "社招", DomainConstants.STATUS_SCREENING, "猎聘", "上海", "35-55K", "high", 0, 14, 14),
                    new Demo("蚂蚁集团", "https://talent.antgroup.com/position/8008", "基础平台开发",
                            "秋招", DomainConstants.STATUS_INTERVIEW, "官网", "杭州", "28-45K", "high", 1, 36, 10));
            for (Demo demo : demos) {
                LocalDateTime appliedAt = LocalDateTime.now().minusDays(demo.appliedDaysAgo);
                ApplicationRecord record = new ApplicationRecord();
                record.setCategory(demo.category);
                record.setCompanyName(demo.company);
                record.setLink(demo.link);
                record.setPosition(demo.position);
                record.setStatus(demo.status);
                record.setChannel(demo.channel);
                record.setCity(demo.city);
                record.setSalary(demo.salary);
                record.setPriority(demo.priority);
                record.setInterviewRound(demo.interviewRound);
                record.setPinned(false);
                record.setAppliedAt(appliedAt);
                record.setNotes("演示数据，可在回收站中删除。");
                applicationMapper.insert(record);
                historyService.recordStatusChange(record.getId(), DomainConstants.STATUS_PENDING, appliedAt);
                historyService.recordStatusChange(record.getId(), DomainConstants.STATUS_SCREENING,
                        appliedAt.plusDays(3));
                if (!DomainConstants.STATUS_SCREENING.equals(demo.status)) {
                    historyService.recordStatusChange(record.getId(), demo.status,
                            appliedAt.plusDays(Math.max(4, demo.appliedDaysAgo - demo.daysSinceStatus)));
                }
            }
            log.info("已写入 {} 条演示投递记录", demos.size());
        } finally {
            TenantContext.clear();
        }
    }

    private record Demo(String company, String link, String position, String category, String status,
                        String channel, String city, String salary, String priority,
                        int interviewRound, int appliedDaysAgo, int daysSinceStatus) {
    }
}
