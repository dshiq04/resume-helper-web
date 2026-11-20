package com.jobtrack.application.dto;

import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
public class BackupImportResult {

    private int applications;
    private int changes;
    private int resumes;
    private int settings;
    private List<String> warnings;
    private Map<String, Long> counts;
}
