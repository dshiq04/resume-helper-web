package com.jobtrack.controller;

import com.jobtrack.system.config.AppProperties;
import com.jobtrack.system.security.SessionService;
import lombok.RequiredArgsConstructor;
import org.springframework.core.env.Environment;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.sql.DataSource;
import java.sql.Connection;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class SystemController {

    private final AppProperties appProperties;
    private final SessionService sessionService;
    private final DataSource dataSource;
    private final Environment environment;

    @GetMapping("/health")
    public Map<String, Object> health() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("status", "ok");
        result.put("version", appProperties.getVersion());
        result.put("time", LocalDateTime.now());
        return result;
    }

    @GetMapping("/system/info")
    public Map<String, Object> info() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("version", appProperties.getVersion());
        result.put("javaVersion", System.getProperty("java.version"));
        result.put("profile", activeProfile());
        result.put("dataDir", appProperties.dataPath().toString());
        result.put("onlineMode", sessionService.mode());
        try (Connection connection = dataSource.getConnection()) {
            String url = connection.getMetaData().getURL();
            result.put("driver", connection.getMetaData().getDatabaseProductName());
            result.put("database", url);
            result.put("location", url);
        } catch (Exception exception) {
            result.put("driver", "unknown");
            result.put("database", "");
            result.put("location", "");
        }
        return result;
    }

    private String activeProfile() {
        String[] profiles = environment.getActiveProfiles();
        return profiles.length == 0 ? "default" : String.join(",", profiles);
    }
}
