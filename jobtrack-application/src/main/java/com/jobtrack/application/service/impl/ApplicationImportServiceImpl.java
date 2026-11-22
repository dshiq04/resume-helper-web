package com.jobtrack.application.service.impl;

import com.jobtrack.application.service.ApplicationImportService;
import com.jobtrack.application.service.ApplicationService;

import com.jobtrack.application.pojo.dto.ApplicationImportDTO;
import com.jobtrack.application.pojo.po.ApplicationRecord;
import com.jobtrack.application.pojo.dto.ApplicationDTO;
import com.jobtrack.application.pojo.vo.ImportResultVO;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.jobtrack.common.constant.DomainConstants;
import com.jobtrack.common.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.fesod.sheet.FesodSheet;
import org.apache.fesod.sheet.context.AnalysisContext;
import org.apache.fesod.sheet.read.listener.ReadListener;
import org.apache.fesod.sheet.write.handler.SheetWriteHandler;
import org.apache.fesod.sheet.write.metadata.holder.WriteSheetHolder;
import org.apache.fesod.sheet.write.metadata.holder.WriteWorkbookHolder;
import org.apache.poi.ss.usermodel.DataValidation;
import org.apache.poi.ss.usermodel.DataValidationConstraint;
import org.apache.poi.ss.usermodel.DataValidationHelper;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.util.CellRangeAddressList;
import org.apache.poi.xssf.usermodel.XSSFDataValidation;
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
public class ApplicationImportServiceImpl implements ApplicationImportService {

    private static final long MAX_BYTES = 10L * 1024 * 1024;
    private static final String[] TEMPLATE_CATEGORIES = {"春招", "秋招", "实习", "社招"};
    /** 下拉与默认行高覆盖到第 1000 行，足够日常导入使用。 */
    private static final int TEMPLATE_VALIDATION_LAST_ROW = 999;
    private static final List<DateTimeFormatter> TIME_FORMATS = List.of(
            DateTimeFormatter.ofPattern("yyyy-M-d"),
            DateTimeFormatter.ofPattern("yyyy/M/d"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"),
            DateTimeFormatter.ofPattern("yyyy-M-d HH:mm"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"),
            DateTimeFormatter.ofPattern("yyyy-M-d HH:mm:ss"));

    private final ApplicationService applicationService;
    private final com.jobtrack.application.mapper.ApplicationRecordMapper applicationRecordMapper;

    public byte[] template() {
        try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            FesodSheet.write(output, ApplicationImportDTO.class)
                    .registerWriteHandler(new TemplateSheetWriteHandler())
                    .sheet("投递导入模板")
                    .doWrite(List.of());
            return output.toByteArray();
        } catch (Exception exception) {
            throw BusinessException.internal("template_failed", "生成导入模板失败");
        }
    }

    /**
     * 按导入模板的列（年度/投递类型/公司/投递地址/时间）导出全部投递记录，
     * 用户注销前可以用它把台账带走。
     */
    public byte[] exportAll() {
        List<ApplicationRecord> records = applicationRecordMapper.selectList(
                Wrappers.<ApplicationRecord>lambdaQuery()
                        .orderByDesc(ApplicationRecord::getAppliedAt));
        List<ApplicationImportDTO> rows = new ArrayList<>();
        DateTimeFormatter timeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
        for (ApplicationRecord record : records) {
            ApplicationImportDTO row = new ApplicationImportDTO();
            row.setYear(record.getApplyYear());
            row.setCategory(categoryLabel(record.getCategory()));
            row.setCompany(record.getCompanyName());
            row.setLink(record.getLink());
            row.setTime(record.getAppliedAt() == null
                    ? "" : record.getAppliedAt().format(timeFormatter));
            rows.add(row);
        }
        try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            FesodSheet.write(output, ApplicationImportDTO.class)
                    .registerWriteHandler(new TemplateSheetWriteHandler())
                    .sheet("投递台账")
                    .doWrite(rows);
            return output.toByteArray();
        } catch (Exception exception) {
            throw BusinessException.internal("export_failed", "导出投递台账失败");
        }
    }

    private String categoryLabel(String category) {
        if (category == null) {
            return "春招";
        }
        return switch (category.toLowerCase(Locale.ROOT)) {
            case "autumn" -> "秋招";
            case "internship" -> "实习";
            case "social" -> "社招";
            default -> "春招";
        };
    }

    public ImportResultVO importFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw BusinessException.badRequest("file_required", "请选择要导入的 Excel 文件");
        }
        if (file.getSize() > MAX_BYTES) {
            throw BusinessException.badRequest("file_too_large", "文件不能超过 10 MB");
        }
        List<ApplicationImportDTO> rows = new ArrayList<>();
        try (InputStream input = file.getInputStream()) {
            FesodSheet.read(input, ApplicationImportDTO.class, new ReadListener<ApplicationImportDTO>() {
                @Override
                public void invoke(ApplicationImportDTO row, AnalysisContext context) {
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
            ApplicationImportDTO row = rows.get(index);
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
        return new ImportResultVO(imported, skipped, rows.size(), errors);
    }

    private ApplicationDTO toRequest(ApplicationImportDTO row) {
        String company = trim(row.getCompany());
        if (company.isEmpty()) {
            throw BusinessException.badRequest("company_required", "公司名称不能为空");
        }
        String link = trim(row.getLink());
        if (link.isEmpty()) {
            throw BusinessException.badRequest("link_required", "投递地址不能为空");
        }
        LocalDateTime appliedAt = parseTime(row.getTime());

        ApplicationDTO request = new ApplicationDTO();
        request.setCompanyName(company);
        request.setLink(link);
        request.setCategory(mapCategory(row.getCategory()));
        request.setStatus(DomainConstants.STATUS_SCREENING);
        Integer year = row.getYear();
        if (year == null || year <= 0) {
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

    /**
     * 生成模板时的样式补充：统一数据行默认行高，并给「投递类型」列加下拉校验。
     */
    private static final class TemplateSheetWriteHandler implements SheetWriteHandler {

        @Override
        public void afterSheetCreate(WriteWorkbookHolder workbookHolder, WriteSheetHolder sheetHolder) {
            Sheet sheet = sheetHolder.getSheet();
            sheet.setDefaultRowHeightInPoints(22f);

            DataValidationHelper helper = sheet.getDataValidationHelper();
            DataValidationConstraint constraint =
                    helper.createExplicitListConstraint(TEMPLATE_CATEGORIES);
            // 第 2 行到第 1000 行，B 列（投递类型）。
            CellRangeAddressList range = new CellRangeAddressList(
                    1, TEMPLATE_VALIDATION_LAST_ROW, 1, 1);
            DataValidation validation = helper.createValidation(constraint, range);
            validation.setShowErrorBox(true);
            validation.setErrorStyle(DataValidation.ErrorStyle.STOP);
            validation.createErrorBox("投递类型不正确", "请从下拉列表中选择：春招、秋招、实习、社招");
            validation.setShowPromptBox(true);
            validation.createPromptBox("投递类型", "请选择：春招、秋招、实习、社招");
            // XSSF 的 showDropDown 标记语义与 HSSF 相反。
            validation.setSuppressDropDownArrow(validation instanceof XSSFDataValidation);
            sheet.addValidationData(validation);
        }
    }
}
