package com.jobtrack.application.cache;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.jobtrack.application.config.CacheNames;
import com.jobtrack.application.config.TenantAwareCacheManager;
import com.jobtrack.application.config.TenantAwareKeyGenerator;
import com.jobtrack.application.mapper.ApplicationChangeMapper;
import com.jobtrack.application.mapper.ApplicationRecordMapper;
import com.jobtrack.application.mapper.DeletedApplicationMapper;
import com.jobtrack.application.mapper.ResumeMapper;
import com.jobtrack.application.pojo.dto.ApplicationDTO;
import com.jobtrack.application.pojo.dto.ResumeUpdateDTO;
import com.jobtrack.application.pojo.po.ApplicationRecord;
import com.jobtrack.application.pojo.po.Resume;
import com.jobtrack.application.service.ApplicationHistoryService;
import com.jobtrack.application.service.ApplicationService;
import com.jobtrack.application.service.FileCrypto;
import com.jobtrack.application.service.ResumeService;
import com.jobtrack.application.service.StatsService;
import com.jobtrack.application.service.TrashService;
import com.jobtrack.application.service.impl.ApplicationServiceImpl;
import com.jobtrack.application.service.impl.ResumeServiceImpl;
import com.jobtrack.application.service.impl.StatsServiceImpl;
import com.jobtrack.application.service.impl.TrashServiceImpl;
import com.jobtrack.common.constant.DomainConstants;
import com.jobtrack.common.context.TenantContext;
import com.jobtrack.system.config.AppProperties;
import com.jobtrack.system.messaging.AsyncTaskPublisher;
import com.jobtrack.system.storage.ObjectStorageService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.CachingConfigurer;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.cache.interceptor.KeyGenerator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 验证 Spring Cache 的命中与失效行为：同一查询只查一次数据库，
 * 增删改后缓存被清理，下一次查询重新读库并回填。
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = ApplicationCacheTest.TestConfig.class)
class ApplicationCacheTest {

    private static final long TENANT_ID = 99L;

    @Autowired
    private ApplicationService applicationService;

    @Autowired
    private StatsService statsService;

    @Autowired
    private ResumeService resumeService;

    @Autowired
    private TrashService trashService;

    @Autowired
    private ApplicationRecordMapper applicationRecordMapper;

    @Autowired
    private ApplicationChangeMapper applicationChangeMapper;

    @Autowired
    private DeletedApplicationMapper deletedApplicationMapper;

    @Autowired
    private ResumeMapper resumeMapper;

    @Autowired
    private ApplicationHistoryService historyService;

    @Autowired
    private CacheManager cacheManager;

    @BeforeEach
    void setUp() {
        TenantContext.set(TENANT_ID);
        for (String name : CacheNames.ALL) {
            var cache = cacheManager.getCache(name);
            if (cache != null) {
                cache.clear();
            }
        }
        Mockito.reset(applicationRecordMapper, applicationChangeMapper, deletedApplicationMapper,
                resumeMapper, historyService);
        when(historyService.loadChanges(anyList())).thenReturn(Map.of());
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void applicationListIsCachedAndEvictedAfterCreate() {
        stubApplicationPage();

        applicationService.list(null, null, null, null, null, null, null, null, 1, 10);
        applicationService.list(null, null, null, null, null, null, null, null, 1, 10);
        verify(applicationRecordMapper, times(1)).selectPage(any(), any());

        applicationService.create(application());

        applicationService.list(null, null, null, null, null, null, null, null, 1, 10);
        verify(applicationRecordMapper, times(2)).selectPage(any(), any());
    }

    @Test
    void applicationYearsAreCachedAndEvictedAfterUpdate() {
        ApplicationRecord record = new ApplicationRecord();
        record.setId(7L);
        record.setCategory(DomainConstants.CATEGORY_AUTUMN);
        record.setCompanyName("示例公司");
        record.setLink("https://example.com/job/7");
        record.setStatus(DomainConstants.STATUS_SCREENING);
        record.setApplyYear(2026);
        when(applicationRecordMapper.selectObjs(any())).thenReturn(List.of(2026));
        when(applicationRecordMapper.selectById(7L)).thenReturn(record);

        applicationService.years();
        applicationService.years();
        verify(applicationRecordMapper, times(1)).selectObjs(any());

        applicationService.update(7L, updateRequest());

        applicationService.years();
        verify(applicationRecordMapper, times(2)).selectObjs(any());
    }

    @Test
    void cacheKeyIsIsolatedByTenant() {
        stubApplicationPage();

        applicationService.list(null, null, null, null, null, null, null, null, 1, 10);
        applicationService.list(null, null, null, null, null, null, null, null, 1, 10);
        verify(applicationRecordMapper, times(1)).selectPage(any(), any());

        TenantContext.set(TENANT_ID + 1);
        applicationService.list(null, null, null, null, null, null, null, null, 1, 10);
        verify(applicationRecordMapper, times(2)).selectPage(any(), any());

        TenantContext.set(TENANT_ID);
        applicationService.list(null, null, null, null, null, null, null, null, 1, 10);
        verify(applicationRecordMapper, times(2)).selectPage(any(), any());

        // 当前租户写入只清理自己的缓存，不应影响另一个租户。
        applicationService.create(application());
        TenantContext.set(TENANT_ID + 1);
        applicationService.list(null, null, null, null, null, null, null, null, 1, 10);
        verify(applicationRecordMapper, times(2)).selectPage(any(), any());

        TenantContext.set(TENANT_ID);
        applicationService.list(null, null, null, null, null, null, null, null, 1, 10);
        verify(applicationRecordMapper, times(3)).selectPage(any(), any());
    }

    @Test
    void statsAreCachedAndEvictedAfterApplicationWrite() {
        when(applicationRecordMapper.selectList(any())).thenReturn(List.of());

        statsService.stats(null, null, null);
        statsService.stats(null, null, null);
        verify(applicationRecordMapper, times(1)).selectList(any());

        applicationService.create(application());

        statsService.stats(null, null, null);
        verify(applicationRecordMapper, times(2)).selectList(any());
    }

    @Test
    void resumeListIsCachedAndEvictedAfterUpdate() {
        Resume resume = new Resume();
        resume.setId(3L);
        resume.setName("Java 后端简历");
        resume.setFileName("resume.pdf");
        resume.setFileType("pdf");
        when(resumeMapper.selectList(any())).thenReturn(List.of(resume));
        when(resumeMapper.selectById(3L)).thenReturn(resume);

        resumeService.list(null);
        resumeService.list(null);
        verify(resumeMapper, times(1)).selectList(any());

        ResumeUpdateDTO request = new ResumeUpdateDTO();
        request.setName("Java 后端简历（更新）");
        resumeService.update(3L, request);

        resumeService.list(null);
        verify(resumeMapper, times(2)).selectList(any());
    }

    @Test
    void trashListIsCachedAndEvictedAfterDelete() {
        when(deletedApplicationMapper.selectList(any())).thenReturn(List.of());
        when(deletedApplicationMapper.deleteById(1L)).thenReturn(1);

        trashService.list();
        trashService.list();
        verify(deletedApplicationMapper, times(1)).selectList(any());

        trashService.delete(1L);

        trashService.list();
        verify(deletedApplicationMapper, times(2)).selectList(any());
    }

    private void stubApplicationPage() {
        when(applicationRecordMapper.selectPage(any(), any())).thenAnswer(invocation -> {
            Page<ApplicationRecord> page = invocation.getArgument(0);
            ApplicationRecord record = new ApplicationRecord();
            record.setId(1L);
            record.setCompanyName("示例公司");
            page.setRecords(List.of(record));
            page.setTotal(1);
            return page;
        });
        when(applicationRecordMapper.insert(any(ApplicationRecord.class))).thenReturn(1);
    }

    private ApplicationDTO application() {
        ApplicationDTO request = new ApplicationDTO();
        request.setCategory(DomainConstants.CATEGORY_AUTUMN);
        request.setCompanyName("示例公司");
        request.setLink("https://example.com/job/1");
        request.setStatus(DomainConstants.STATUS_SCREENING);
        request.setYear(2026);
        return request;
    }

    private com.jobtrack.application.pojo.dto.ApplicationUpdateDTO updateRequest() {
        var request = new com.jobtrack.application.pojo.dto.ApplicationUpdateDTO();
        request.setCategory(DomainConstants.CATEGORY_AUTUMN);
        request.setCompanyName("示例公司");
        request.setLink("https://example.com/job/7");
        request.setStatus(DomainConstants.STATUS_SCREENING);
        request.setYear(2025);
        return request;
    }

    @Configuration(proxyBeanMethods = false)
    @EnableCaching
    static class TestConfig implements CachingConfigurer {

        /** 单元测试不依赖真实 Redis，用进程内缓存验证注解语义。 */
        @Bean
        @Override
        public CacheManager cacheManager() {
            return new TenantAwareCacheManager(new ConcurrentMapCacheManager());
        }

        @Bean
        @Override
        public KeyGenerator keyGenerator() {
            return new TenantAwareKeyGenerator();
        }

        @Bean
        ApplicationRecordMapper applicationRecordMapper() {
            return mock(ApplicationRecordMapper.class);
        }

        @Bean
        ApplicationChangeMapper applicationChangeMapper() {
            return mock(ApplicationChangeMapper.class);
        }

        @Bean
        DeletedApplicationMapper deletedApplicationMapper() {
            return mock(DeletedApplicationMapper.class);
        }

        @Bean
        ResumeMapper resumeMapper() {
            return mock(ResumeMapper.class);
        }

        @Bean
        ApplicationHistoryService applicationHistoryService() {
            return mock(ApplicationHistoryService.class);
        }

        @Bean
        TrashService trashService(ApplicationRecordMapper applicationRecordMapper,
                                  ApplicationChangeMapper applicationChangeMapper,
                                  DeletedApplicationMapper deletedApplicationMapper,
                                  ApplicationHistoryService historyService) {
            return new TrashServiceImpl(applicationRecordMapper, applicationChangeMapper,
                    deletedApplicationMapper, historyService);
        }

        @Bean
        ApplicationService applicationService(ApplicationRecordMapper applicationRecordMapper,
                                              ApplicationChangeMapper applicationChangeMapper,
                                              ApplicationHistoryService historyService,
                                              TrashService trashService) {
            return new ApplicationServiceImpl(applicationRecordMapper, applicationChangeMapper,
                    historyService, trashService);
        }

        @Bean
        StatsService statsService(ApplicationRecordMapper applicationRecordMapper,
                                  ResumeMapper resumeMapper,
                                  ApplicationHistoryService historyService) {
            return new StatsServiceImpl(applicationRecordMapper, resumeMapper, historyService);
        }

        @Bean
        ResumeService resumeService(ResumeMapper resumeMapper, FileCrypto fileCrypto,
                                    AppProperties appProperties, ObjectStorageService objectStorage,
                                    AsyncTaskPublisher publisher) {
            return new ResumeServiceImpl(resumeMapper, fileCrypto, appProperties, objectStorage, publisher);
        }

        @Bean
        FileCrypto fileCrypto() {
            return mock(FileCrypto.class);
        }

        @Bean
        AppProperties appProperties() {
            return mock(AppProperties.class);
        }

        @Bean
        ObjectStorageService objectStorageService() {
            return mock(ObjectStorageService.class);
        }

        @Bean
        AsyncTaskPublisher asyncTaskPublisher() {
            return mock(AsyncTaskPublisher.class);
        }
    }
}
