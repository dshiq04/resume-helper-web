package com.jobtrack.application.controller;

import com.jobtrack.application.dto.BackupImportResult;
import com.jobtrack.application.service.BackupService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@RestController
@RequestMapping("/api/system/backup")
@RequiredArgsConstructor
public class BackupController {

    private final BackupService backupService;

    @GetMapping("/export")
    public ResponseEntity<byte[]> exportData() {
        byte[] content = backupService.exportData();
        String stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmm"));
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("application/zip"))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"jobtrack-backup-" + stamp + ".zip\"")
                .body(content);
    }

    @PostMapping("/import")
    public BackupImportResult importData(
            @RequestParam("file") MultipartFile file,
            @RequestParam(defaultValue = "false") boolean includeTrash) {
        return backupService.importData(file, includeTrash);
    }
}
