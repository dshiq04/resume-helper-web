package com.jobtrack.application.controller;

import com.jobtrack.application.pojo.ApplicationChangeView;
import com.jobtrack.application.pojo.ApplicationRequest;
import com.jobtrack.application.pojo.ApplicationUpdateRequest;
import com.jobtrack.application.pojo.BatchCategoryRequest;
import com.jobtrack.application.pojo.BatchStatusRequest;
import com.jobtrack.application.pojo.HistoryResponse;
import com.jobtrack.application.pojo.IdsRequest;
import com.jobtrack.application.pojo.ApplicationRecord;
import com.jobtrack.application.pojo.ImportResult;
import com.jobtrack.application.service.ApplicationImportService;
import com.jobtrack.application.service.ApplicationService;
import com.jobtrack.common.api.PageResult;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class ApplicationController {

    private final ApplicationService applicationService;
    private final ApplicationImportService applicationImportService;

    @GetMapping("/applications")
    public PageResult<ApplicationRecord> list(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String channel,
            @RequestParam(required = false) String priority,
            @RequestParam(required = false) Integer year,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int pageSize) {
        return applicationService.list(search, category, status, channel, priority,
                year, from, to, page, pageSize);
    }

    @GetMapping("/applications/template")
    public ResponseEntity<byte[]> template() {
        byte[] content = applicationImportService.template();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"resume-trail-import-template.xlsx\"")
                .contentType(MediaType.parseMediaType(
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(content);
    }

    @PostMapping("/applications/import")
    public ImportResult importApplications(@RequestParam("file") MultipartFile file) {
        return applicationImportService.importFile(file);
    }

    /** 按导入模板的列导出当前工作区的全部投递记录（注销账号前可用）。 */
    @GetMapping("/applications/export")
    public ResponseEntity<byte[]> exportApplications() {
        byte[] content = applicationImportService.exportAll();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"resume-trail-applications.xlsx\"")
                .contentType(MediaType.parseMediaType(
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(content);
    }

    @GetMapping("/applications/years")
    public com.jobtrack.application.pojo.YearList years() {
        return applicationService.years();
    }

    @PostMapping("/applications")
    @ResponseStatus(HttpStatus.CREATED)
    public ApplicationRecord create(@Valid @RequestBody ApplicationRequest request) {
        return applicationService.create(request);
    }

    @GetMapping("/application/{id}")
    public ApplicationRecord get(@PathVariable Long id) {
        return applicationService.get(id);
    }

    @PutMapping("/application/{id}")
    public ApplicationRecord update(@PathVariable Long id,
                                    @Valid @RequestBody ApplicationUpdateRequest request) {
        return applicationService.update(id, request);
    }

    @DeleteMapping("/application/{id}")
    public Map<String, Object> delete(@PathVariable Long id) {
        applicationService.delete(id);
        return Map.of("deleted", 1);
    }

    @PostMapping("/applications/batch-delete")
    public Map<String, Object> batchDelete(@Valid @RequestBody IdsRequest request) {
        return Map.of("deleted", applicationService.batchDelete(request.getIds()));
    }

    @PatchMapping("/applications/batch-status")
    public Map<String, Object> batchStatus(@Valid @RequestBody BatchStatusRequest request) {
        return Map.of("updated", applicationService.batchStatus(request.getIds(), request.getStatus()));
    }

    @PatchMapping("/applications/batch-category")
    public Map<String, Object> batchCategory(@Valid @RequestBody BatchCategoryRequest request) {
        return Map.of("updated", applicationService.batchCategory(request.getIds(), request.getCategory()));
    }

    @GetMapping("/applications/companies")
    public Map<String, Object> companies(@RequestParam(required = false) String search) {
        return Map.of("items", applicationService.companySuggestions(search));
    }

    @GetMapping("/application/{id}/history")
    public HistoryResponse history(@PathVariable Long id) {
        return applicationService.history(id);
    }

    @PostMapping("/application/{id}/history")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> appendHistory(@PathVariable Long id,
                                             @RequestBody Map<String, String> body) {
        List<ApplicationChangeView> items = applicationService.appendHistory(
                id, body.get("status"), body.get("changedAt"));
        return Map.of("items", items);
    }
}
