package com.jobtrack.system.controller;

import com.jobtrack.system.config.AppProperties;
import com.jobtrack.system.database.DatabaseManager;
import com.jobtrack.system.dto.DatabaseTestResult;
import com.jobtrack.system.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.core.env.Environment;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 数据库信息查询。数据库连接由 application-{profile}.yml 决定，
 * 这里只提供只读展示与连通性测试，避免运行期改写配置文件。
 */
@RestController
@RequestMapping("/api/system/database")
@RequiredArgsConstructor
public class DatabaseController {

    private final DatabaseManager databaseManager;
    private final AppProperties appProperties;
    private final Environment environment;

    @GetMapping
    public Map<String, Object> current() {
        SecurityUtils.requireAdmin();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("settings", databaseManager.currentSettings());
        result.put("supportedTypes", List.of("mysql", "postgresql"));
        result.put("profile", activeProfile());
        result.put("configFile", "application-" + activeProfile() + ".yml");
        result.put("dataDir", appProperties.dataPath().toString());
        result.put("editable", false);
        return result;
    }

    @GetMapping("/test")
    public DatabaseTestResult test() {
        SecurityUtils.requireAdmin();
        return databaseManager.testCurrent();
    }

    private String activeProfile() {
        String[] profiles = environment.getActiveProfiles();
        return profiles.length == 0 ? "default" : String.join(",", profiles);
    }
}
