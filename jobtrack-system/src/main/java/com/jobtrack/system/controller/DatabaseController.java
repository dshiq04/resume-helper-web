package com.jobtrack.system.controller;

import com.jobtrack.system.config.AppProperties;
import com.jobtrack.system.config.DatabaseSettings;
import com.jobtrack.system.database.DatabaseManager;
import com.jobtrack.system.dto.DatabaseSwitchResult;
import com.jobtrack.system.dto.DatabaseTestResult;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/system/database")
@RequiredArgsConstructor
public class DatabaseController {

    private final DatabaseManager databaseManager;
    private final AppProperties appProperties;

    @GetMapping
    public Map<String, Object> current() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("settings", databaseManager.currentSettings());
        result.put("supportedTypes", List.of("sqlite", "mysql", "postgresql"));
        result.put("configPath", databaseManager.configPath().toString());
        result.put("dataDir", appProperties.dataPath().toString());
        return result;
    }

    @PostMapping("/test")
    public DatabaseTestResult test(@RequestBody DatabaseSettings input) {
        return databaseManager.test(input);
    }

    @PutMapping
    public DatabaseSwitchResult switchDatabase(@RequestBody DatabaseSettings input) {
        return databaseManager.switchTo(input);
    }
}
