package com.jobtrack.application.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 数据备份的 manifest.json。
 */
@Data
public class BackupManifest {

    private int version = 1;
    private LocalDateTime exportedAt;
    private String appVersion;
    private String tenantName;
    private Map<String, Long> counts;
    private List<String> warnings;
}
