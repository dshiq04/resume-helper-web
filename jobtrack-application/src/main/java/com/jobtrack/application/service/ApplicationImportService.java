package com.jobtrack.application.service;

import com.jobtrack.application.dto.ApplicationImportRow;
import com.jobtrack.application.dto.ApplicationRequest;
import com.jobtrack.application.dto.ImportResult;
import com.jobtrack.common.constant.DomainConstants;
import com.jobtrack.common.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.fesod.sheet.FesodSheet;
import org.apache.fesod.sheet.context.AnalysisContext;
import org.apache.fesod.sheet.read.listener.ReadListener;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 投递台账 Excel 导入：模板列为 年度 / 投递类型 / 公司 / 投递地址 / 时间。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ApplicationImportService {

    private static final long MAX_BYTES = 10L * 1024 * 1024;
    private static final List<DateTimeFormatter> TIME_FORMATS = List.of(
            DateTimeFormatter.ofPattern("yyyy-M-d"),
            DateTimeFormatter.ofPattern("yyyy/M/d"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"),
            DateTimeFormatter.ofPattern("yyyy-M-d HH:mm"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"),
            DateTimeFormatter.ofPattern("yyyy-M-d HH:mm:ss"));

    private final ApplicationService applicationService;

    public byte[] template() {
        try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            FesodSheet.write(output, ApplicationImportRow.class)
                    .sheet("投递导入模板")
                    .doWrite(List.of());
            return output.toByteArray();
        } catch (Exception exception) {
            throw BusinessException.internal("template_failed", "生成导入模板失败");
        }
    }

    public ImportResult importFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw BusinessException.badRequest("file_required", "请选择要导入的 Excel 文件");
        }
        if (file.getSize() > MAX_BYTES) {
            throw BusinessException.badRequest("file_too_large", "文件不能超过 10 MB");
        }
        List<ApplicationImportRow> rows = new ArrayList<>();
        try (InputStream input = file.getInputStream()) {
            FesodSheet.read(input, ApplicationImportRow.class, new ReadListener<ApplicationImportRow>() {
                @Override
                public void invoke(ApplicationImportRow row, AnalysisContext context) {
                    if (row != null) {
                        rows.add(row);
                    }
                }

                @Override
                public void doAfterAllAnalysed(AnalysisContext context) {
                    // 无需额外处理。
                }
            }).sheet().doRead();
        } catch (Exception exception) {
            throw BusinessException.badRequest("invalid_excel",
                    "无法解析 Excel，请使用提供的模板");
        }

        int imported = 0;
        int skipped = 0;
        List<String> errors = new ArrayList<>();
        for (int index = 0; index < rows.size(); index++) {
            ApplicationImportRow row = rows.get(index);
            try {
                applicationService.create(toRequest(row));
                imported++;
            } catch (BusinessException exception) {
                skipped++;
                addError(errors, index, exception.getMessage());
            } catch (Exception exception) {
                skipped++;
                addError(errors, index, "该行数据不完整");
            }
        }
        return new ImportResult(imported, skipped, rows.size(), errors);
    }

    private ApplicationRequest toRequest(ApplicationImportRow row) {
        String company = trim(row.getCompany());
        if (company.isEmpty()) {
            throw BusinessException.badRequest("company_required", "公司名称不能为空");
        }
        String link = trim(row.getLink());
        if (link.isEmpty()) {
            throw BusinessException.badRequest("link_required", "投递地址不能为空");
        }
        LocalDateTime appliedAt = parseTime(row.getTime());

        ApplicationRequest request = new ApplicationRequest();
        request.setCompanyName(company);
        request.setLink(link);
        request.setCategory(mapCategory(row.getCategory()));
        request.setStatus(DomainConstants.STATUS_SCREENING);
        Integer year = row.getYear();
        if (year == null || year < 2000 || year > 2100) {
            year = appliedAt == null ? LocalDate.now().getYear() : appliedAt.getYear();
        }
        request.setYear(year);
        request.setAppliedAt(appliedAt);
        return request;
    }

    private String mapCategory(String raw) {
        String value = trim(raw).toLowerCase(Locale.ROOT);
        return switch (value) {
            case "spring", "春招" -> DomainConstants.CATEGORY_SPRING;
            case "autumn", "秋招" -> DomainConstants.CATEGORY_AUTUMN;
            case "internship", "实习" -> DomainConstants.CATEGORY_INTERNSHIP;
            case "social", "社招", "社会招聘" -> DomainConstants.CATEGORY_SOCIAL;
            default -> DomainConstants.CATEGORY_SPRING;
        };
    }

    private LocalDateTime parseTime(String raw) {
        String value = trim(raw);
        if (value.isEmpty()) {
            return null;
        }
        for (DateTimeFormatter formatter : TIME_FORMATS) {
            try {
                if (value.length() <= 10) {
                    return LocalDate.parse(value, formatter).atStartOfDay();
                }
                return LocalDateTime.parse(value, formatter);
            } catch (Exception ignored) {
                // 尝试下一个格式。
            }
        }
        throw BusinessException.badRequest("invalid_time", "时间格式应为 yyyy-MM-dd");
    }

    private void addError(List<String> errors, int index, String message) {
        if (errors.size() < 20) {
            errors.add("第 " + (index + 2) + " 行：" + message);
        }
    }

    private String trim(String value) {
        return value == null ? "" : value.trim();
    }
}
