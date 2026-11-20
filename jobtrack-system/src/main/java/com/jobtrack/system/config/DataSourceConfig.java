package com.jobtrack.system.config;

import com.jobtrack.system.database.DatabaseManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import javax.sql.DataSource;

/**
 * 用动态 DataSource 替换 Spring Boot 自动配置的数据源，
 * 让设置页切换数据库时不需要重建 SqlSessionFactory。
 */
@Configuration
public class DataSourceConfig {

    @Bean
    @Primary
    public DataSource dataSource(DatabaseManager databaseManager) {
        return databaseManager.dataSource();
    }
}
