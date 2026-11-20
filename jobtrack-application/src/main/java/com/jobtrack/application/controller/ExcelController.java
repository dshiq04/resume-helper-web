package com.jobtrack.application.controller;

import com.jobtrack.application.dto.ImportResult;
import com.jobtrack.application.service.ExcelService;
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

@RestController
@RequestMapping("/api/applications")
@RequiredArgsConstructor
public class ExcelController {

    private final ExcelService excelService;

    @GetMapping("/template")
    public ResponseEntity<byte[]> template() {
        byte[] content = excelService.template();
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"jobtrack-import-template.xlsx\"")
                .body(content);
    }

    @PostMapping("/import")
    public ImportResult importFile(@RequestParam("file") MultipartFile file) {
        return excelService.importWorkbook(file);
    }
}
