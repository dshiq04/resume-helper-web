package com.jobtrack.application.controller;

import com.jobtrack.common.api.Result;

import com.jobtrack.application.pojo.vo.ApplicationChangeVO;
import com.jobtrack.application.pojo.dto.ApplicationDTO;
import com.jobtrack.application.pojo.dto.ApplicationUpdateDTO;
import com.jobtrack.application.pojo.dto.BatchCategoryDTO;
import com.jobtrack.application.pojo.dto.BatchStatusDTO;
import com.jobtrack.application.pojo.vo.HistoryVO;
import com.jobtrack.application.pojo.dto.IdsDTO;
import com.jobtrack.application.pojo.po.ApplicationRecord;
import com.jobtrack.application.pojo.vo.ImportResultVO;
import com.jobtrack.application.service.ApplicationImportService;
import com.jobtrack.application.service.ApplicationService;
import com.jobtrack.common.api.PageResult;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class ApplicationController {

    private final ApplicationService applicationService;
    private final ApplicationImportService applicationImportService;

    @GetMapping("/applications")
    public Result<PageResult<ApplicationRecord>> list(
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
        return Result.success(applicationService.list(search, category, status, channel, priority,
                year, from, to, page, pageSize));
    }

    @GetMapping("/applications/template")
    public void template(HttpServletResponse response) throws IOException {
        writeFile(response, applicationImportService.template(),
                "resume-trail-import-template.xlsx");
    }

    @PostMapping("/applications/import")
    public Result<ImportResultVO> importApplications(@RequestParam("file") MultipartFile file) {
        return Result.success(applicationImportService.importFile(file));
    }

    /** 按导入模板的列导出当前工作区的全部投递记录（注销账号前可用）。 */
    @GetMapping("/applications/export")
    public void exportApplications(HttpServletResponse response) throws IOException {
        writeFile(response, applicationImportService.exportAll(),
                "resume-trail-applications.xlsx");
    }

    private void writeFile(HttpServletResponse response, byte[] content, String fileName)
            throws IOException {
        response.setContentType(
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setHeader("Content-Disposition", "attachment; filename=\"" + fileName + "\"");
        response.setContentLength(content.length);
        response.getOutputStream().write(content);
    }

    @GetMapping("/applications/years")
    public Result<com.jobtrack.application.pojo.vo.YearListVO> years() {
        return Result.success(applicationService.years());
    }

    @PostMapping("/applications")
    public Result<ApplicationRecord> create(@Valid @RequestBody ApplicationDTO request) {
        return Result.success(applicationService.create(request));
    }

    @GetMapping("/application/{id}")
    public Result<ApplicationRecord> get(@PathVariable Long id) {
        return Result.success(applicationService.get(id));
    }

    @PutMapping("/application/{id}")
    public Result<ApplicationRecord> update(@PathVariable Long id,
                                    @Valid @RequestBody ApplicationUpdateDTO request) {
        return Result.success(applicationService.update(id, request));
    }

    @DeleteMapping("/application/{id}")
    public Result<Map<String, Object>> delete(@PathVariable Long id) {
        applicationService.delete(id);
        return Result.success(Map.of("deleted", 1));
    }

    @PostMapping("/applications/batch-delete")
    public Result<Map<String, Object>> batchDelete(@Valid @RequestBody IdsDTO request) {
        return Result.success(Map.of("deleted", applicationService.batchDelete(request.getIds())));
    }

    @PatchMapping("/applications/batch-status")
    public Result<Map<String, Object>> batchStatus(@Valid @RequestBody BatchStatusDTO request) {
        return Result.success(Map.of("updated", applicationService.batchStatus(request.getIds(), request.getStatus())));
    }

    @PatchMapping("/applications/batch-category")
    public Result<Map<String, Object>> batchCategory(@Valid @RequestBody BatchCategoryDTO request) {
        return Result.success(Map.of("updated", applicationService.batchCategory(request.getIds(), request.getCategory())));
    }

    @GetMapping("/applications/companies")
    public Result<Map<String, Object>> companies(@RequestParam(required = false) String search) {
        return Result.success(Map.of("items", applicationService.companySuggestions(search)));
    }

    @GetMapping("/application/{id}/history")
    public Result<HistoryVO> history(@PathVariable Long id) {
        return Result.success(applicationService.history(id));
    }

    @PostMapping("/application/{id}/history")
    public Result<Map<String, Object>> appendHistory(@PathVariable Long id,
                                             @RequestBody Map<String, String> body) {
        List<ApplicationChangeVO> items = applicationService.appendHistory(
                id, body.get("status"), body.get("changedAt"));
        return Result.success(Map.of("items", items));
    }
}
