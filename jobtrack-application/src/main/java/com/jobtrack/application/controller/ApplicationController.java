package com.jobtrack.application.controller;

import com.jobtrack.application.dto.ApplicationChangeView;
import com.jobtrack.application.dto.ApplicationRequest;
import com.jobtrack.application.dto.ApplicationUpdateRequest;
import com.jobtrack.application.dto.BatchCategoryRequest;
import com.jobtrack.application.dto.BatchStatusRequest;
import com.jobtrack.application.dto.HistoryResponse;
import com.jobtrack.application.dto.IdsRequest;
import com.jobtrack.application.entity.ApplicationRecord;
import com.jobtrack.application.service.ApplicationService;
import com.jobtrack.common.api.PageResult;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
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

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class ApplicationController {

    private final ApplicationService applicationService;

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

    @GetMapping("/applications/years")
    public com.jobtrack.application.dto.YearList years() {
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
                                    @RequestBody ApplicationUpdateRequest request) {
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
