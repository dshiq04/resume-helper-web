package com.jobtrack.application.service;

import com.jobtrack.application.dto.ImportResult;
import com.jobtrack.application.entity.ApplicationRecord;
import com.jobtrack.application.mapper.ApplicationRecordMapper;
import com.jobtrack.common.constant.DomainConstants;
import com.jobtrack.common.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.DataValidation;
import org.apache.poi.ss.usermodel.DataValidationHelper;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.apache.poi.ss.util.CellRangeAddressList;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class ExcelService {

    private static final int MAX_ROWS = 5000;
    private static final long MAX_SIZE = 10L * 1024 * 1024;

    private static final Map<String, String> CATEGORY_ALIASES = Map.ofEntries(
            Map.entry("spring", DomainConstants.CATEGORY_SPRING),
            Map.entry("springrecruit", DomainConstants.CATEGORY_SPRING),
            Map.entry("春招", DomainConstants.CATEGORY_SPRING),
            Map.entry("春季招聘", DomainConstants.CATEGORY_SPRING),
            Map.entry("春採用", DomainConstants.CATEGORY_SPRING),
            Map.entry("봄채용", DomainConstants.CATEGORY_SPRING),
            Map.entry("autumn", DomainConstants.CATEGORY_AUTUMN),
            Map.entry("fall", DomainConstants.CATEGORY_AUTUMN),
            Map.entry("秋招", DomainConstants.CATEGORY_AUTUMN),
            Map.entry("秋季招聘", DomainConstants.CATEGORY_AUTUMN),
            Map.entry("秋採用", DomainConstants.CATEGORY_AUTUMN),
            Map.entry("가을채용", DomainConstants.CATEGORY_AUTUMN),
            Map.entry("internship", DomainConstants.CATEGORY_INTERNSHIP),
            Map.entry("intern", DomainConstants.CATEGORY_INTERNSHIP),
            Map.entry("实习", DomainConstants.CATEGORY_INTERNSHIP),
            Map.entry("實習", DomainConstants.CATEGORY_INTERNSHIP),
            Map.entry("インターン", DomainConstants.CATEGORY_INTERNSHIP),
            Map.entry("인턴", DomainConstants.CATEGORY_INTERNSHIP),
            Map.entry("social", DomainConstants.CATEGORY_SOCIAL),
            Map.entry("experienced", DomainConstants.CATEGORY_SOCIAL),
            Map.entry("社招", DomainConstants.CATEGORY_SOCIAL),
            Map.entry("社会招聘", DomainConstants.CATEGORY_SOCIAL),
            Map.entry("社會招聘", DomainConstants.CATEGORY_SOCIAL),
            Map.entry("中途採用", DomainConstants.CATEGORY_SOCIAL),
            Map.entry("경력채용", DomainConstants.CATEGORY_SOCIAL));

    private static final Map<String, String> STATUS_ALIASES = Map.ofEntries(
            Map.entry("screening", DomainConstants.STATUS_SCREENING),
            Map.entry("screen", DomainConstants.STATUS_SCREENING),
            Map.entry("初筛", DomainConstants.STATUS_SCREENING),
            Map.entry("初篩", DomainConstants.STATUS_SCREENING),
            Map.entry("简历筛选", DomainConstants.STATUS_SCREENING),
            Map.entry("書類選考", DomainConstants.STATUS_SCREENING),
            Map.entry("서류전형", DomainConstants.STATUS_SCREENING),
            Map.entry("written", DomainConstants.STATUS_WRITTEN),
            Map.entry("test", DomainConstants.STATUS_WRITTEN),
            Map.entry("笔试", DomainConstants.STATUS_WRITTEN),
            Map.entry("筆試", DomainConstants.STATUS_WRITTEN),
            Map.entry("筆記試験", DomainConstants.STATUS_WRITTEN),
            Map.entry("필기", DomainConstants.STATUS_WRITTEN),
            Map.entry("interview", DomainConstants.STATUS_INTERVIEW),
            Map.entry("面试", DomainConstants.STATUS_INTERVIEW),
            Map.entry("面試", DomainConstants.STATUS_INTERVIEW),
            Map.entry("面接", DomainConstants.STATUS_INTERVIEW),
            Map.entry("면접", DomainConstants.STATUS_INTERVIEW),
            Map.entry("pending", DomainConstants.STATUS_PENDING),
            Map.entry("hold", DomainConstants.STATUS_PENDING),
            Map.entry("待定", DomainConstants.STATUS_PENDING),
            Map.entry("保留", DomainConstants.STATUS_PENDING),
            Map.entry("未定", DomainConstants.STATUS_PENDING),
            Map.entry("보류", DomainConstants.STATUS_PENDING),
            Map.entry("offer", DomainConstants.STATUS_OFFER),
            Map.entry("录用", DomainConstants.STATUS_OFFER),
            Map.entry("錄用", DomainConstants.STATUS_OFFER),
            Map.entry("内定", DomainConstants.STATUS_OFFER),
            Map.entry("오퍼", DomainConstants.STATUS_OFFER),
            Map.entry("rejected", DomainConstants.STATUS_REJECTED),
            Map.entry("reject", DomainConstants.STATUS_REJECTED),
            Map.entry("淘汰", DomainConstants.STATUS_REJECTED),
            Map.entry("拒绝", DomainConstants.STATUS_REJECTED),
            Map.entry("拒絕", DomainConstants.STATUS_REJECTED),
            Map.entry("탈락", DomainConstants.STATUS_REJECTED));

    private final ApplicationRecordMapper applicationMapper;
    private final ApplicationHistoryService historyService;
    private final ApplicationCacheService applicationCache;

    public byte[] template() {
        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            Sheet data = workbook.createSheet("投递记录");
            Sheet help = workbook.createSheet("填写说明");

            CellStyle headerStyle = workbook.createCellStyle();
            Font headerFont = workbook.createFont();
            headerFont.setBold(true);
            headerFont.setColor(IndexedColors.WHITE.getIndex());
            headerStyle.setFont(headerFont);
            headerStyle.setFillForegroundColor(IndexedColors.DARK_GREEN.getIndex());
            headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);

            String[] headers = {
                    "投递类型 / Category", "公司名称 / Company", "投递链接 / Link", "年份 / Year",
                    "岗位 / Position", "状态 / Status", "渠道 / Channel", "城市 / City",
                    "薪资 / Salary", "优先级 / Priority", "备注 / Notes"
            };
            Row headerRow = data.createRow(0);
            for (int index = 0; index < headers.length; index++) {
                Cell cell = headerRow.createCell(index);
                cell.setCellValue(headers[index]);
                cell.setCellStyle(headerStyle);
            }
            data.setColumnWidth(0, 18 * 256);
            data.setColumnWidth(1, 30 * 256);
            data.setColumnWidth(2, 44 * 256);
            data.setColumnWidth(3, 12 * 256);
            data.setColumnWidth(4, 26 * 256);
            data.setColumnWidth(5, 16 * 256);
            data.setColumnWidth(6, 18 * 256);
            data.setColumnWidth(7, 16 * 256);
            data.setColumnWidth(8, 18 * 256);
            data.setColumnWidth(9, 14 * 256);
            data.setColumnWidth(10, 40 * 256);
            data.createFreezePane(0, 1);

            DataValidationHelper helper = data.getDataValidationHelper();
            DataValidation categoryValidation = helper.createValidation(
                    helper.createExplicitListConstraint(new String[]{
                            "春招", "秋招", "实习", "社招", "spring", "autumn", "internship", "social"}),
                    new CellRangeAddressList(1, MAX_ROWS, 0, 0));
            categoryValidation.setSuppressDropDownArrow(true);
            data.addValidationData(categoryValidation);

            DataValidation statusValidation = helper.createValidation(
                    helper.createExplicitListConstraint(new String[]{
                            "初筛", "笔试", "面试", "待定", "Offer", "淘汰",
                            "screening", "written", "interview", "pending", "offer", "rejected"}),
                    new CellRangeAddressList(1, MAX_ROWS, 5, 5));
            statusValidation.setSuppressDropDownArrow(true);
            data.addValidationData(statusValidation);

            String[][] helpRows = {
                    {"投递手账 · Excel 导入说明"},
                    {""},
                    {"1. 只有公司名称与投递链接是必填项，其余列可以留空。"},
                    {"2. 年份留空时使用导入时的当前年份，可用于整理往年的投递记录。"},
                    {"3. 投递类型支持：春招 / 秋招 / 实习 / 社招，或 spring / autumn / internship / social。"},
                    {"4. 状态支持：初筛 / 笔试 / 面试 / 待定 / Offer / 淘汰。"},
                    {"5. 投递链接按原样保存，不会自动补全 http:// 或 https://。"},
                    {"6. 空白的投递类型默认春招，空白状态默认初筛。"},
                    {""},
                    {"示例："},
                    {"春招", "示例科技", "https://example.com/careers/123", "2026", "后端开发", "初筛",
                            "官网", "杭州", "20-30K", "高", "内推码 ABC123"},
                    {"秋招", "Example Inc.", "https://example.com/jobs/456", "2025", "Frontend Engineer", "面试",
                            "内推", "上海", "25-35K", "中", ""}
            };
            for (int rowIndex = 0; rowIndex < helpRows.length; rowIndex++) {
                Row row = help.createRow(rowIndex);
                for (int columnIndex = 0; columnIndex < helpRows[rowIndex].length; columnIndex++) {
                    row.createCell(columnIndex).setCellValue(helpRows[rowIndex][columnIndex]);
                }
            }
            help.setColumnWidth(0, 20 * 256);
            help.setColumnWidth(1, 24 * 256);
            help.setColumnWidth(2, 42 * 256);
            help.setColumnWidth(3, 24 * 256);

            workbook.setActiveSheet(0);
            workbook.write(output);
            return output.toByteArray();
        } catch (IOException exception) {
            throw BusinessException.internal("template_failed", "生成导入模板失败");
        }
    }

    @Transactional
    public ImportResult importWorkbook(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw BusinessException.badRequest("file_required", "请选择 Excel 文件");
        }
        if (file.getSize() > MAX_SIZE) {
            throw BusinessException.badRequest("file_too_large", "Excel 文件不能超过 10 MB");
        }
        String name = file.getOriginalFilename() == null ? "" : file.getOriginalFilename().toLowerCase(Locale.ROOT);
        if (!name.endsWith(".xlsx") && !name.endsWith(".xlsm")) {
            throw BusinessException.badRequest("unsupported_file", "仅支持 .xlsx 与 .xlsm 文件");
        }

        try (InputStream input = file.getInputStream(); Workbook workbook = WorkbookFactory.create(input)) {
            if (workbook.getNumberOfSheets() == 0) {
                throw BusinessException.badRequest("empty_workbook", "工作簿没有工作表");
            }
            Sheet sheet = workbook.getSheetAt(0);
            DataFormatter formatter = new DataFormatter();
            int headerIndex = findHeaderRow(sheet, formatter);
            if (headerIndex < 0) {
                throw BusinessException.badRequest("invalid_header", "未找到有效表头（至少需要公司名称列）");
            }
            Map<String, Integer> columns = mapColumns(sheet.getRow(headerIndex), formatter);
            if (!columns.containsKey("company")) {
                throw BusinessException.badRequest("invalid_header", "缺少公司名称列");
            }
            if (!columns.containsKey("link")) {
                throw BusinessException.badRequest("invalid_header", "缺少投递链接列（链接为必填项）");
            }

            int lastRow = sheet.getLastRowNum();
            if (lastRow - headerIndex > MAX_ROWS) {
                throw BusinessException.badRequest("too_many_rows", "最多导入 " + MAX_ROWS + " 行");
            }

            List<String> errors = new ArrayList<>();
            int imported = 0;
            int skipped = 0;
            LocalDateTime appliedAt = LocalDateTime.now();

            for (int rowIndex = headerIndex + 1; rowIndex <= lastRow; rowIndex++) {
                Row row = sheet.getRow(rowIndex);
                if (row == null || rowIsEmpty(row, formatter)) {
                    continue;
                }
                int displayRow = rowIndex + 1;
                String company = cell(row, columns.get("company"), formatter).trim();
                String link = cell(row, columns.get("link"), formatter).trim();
                if (company.isEmpty()) {
                    skipped++;
                    addError(errors, displayRow, "公司名称不能为空");
                    continue;
                }
                if (link.isEmpty()) {
                    skipped++;
                    addError(errors, displayRow, "投递链接不能为空");
                    continue;
                }
                if (company.length() > 255) {
                    skipped++;
                    addError(errors, displayRow, "公司名称超过 255 个字符");
                    continue;
                }
                if (link.length() > 2048) {
                    skipped++;
                    addError(errors, displayRow, "投递链接超过 2048 个字符");
                    continue;
                }

                String yearRaw = cell(row, columns.get("year"), formatter).trim();
                int recordYear = appliedAt.getYear();
                if (!yearRaw.isBlank()) {
                    try {
                        recordYear = Integer.parseInt(yearRaw.replaceAll("\\.0+$", ""));
                    } catch (NumberFormatException exception) {
                        skipped++;
                        addError(errors, displayRow, "年份格式不正确: " + yearRaw);
                        continue;
                    }
                    if (recordYear < 2000 || recordYear > 2100) {
                        skipped++;
                        addError(errors, displayRow, "年份需在 2000-2100 之间");
                        continue;
                    }
                }

                String categoryRaw = cell(row, columns.get("category"), formatter);
                String category = DomainConstants.CATEGORY_SPRING;
                if (!categoryRaw.isBlank()) {
                    String parsed = parseCategory(categoryRaw);
                    if (parsed == null) {
                        skipped++;
                        addError(errors, displayRow, "无法识别的投递类型: " + categoryRaw);
                        continue;
                    }
                    category = parsed;
                }

                String statusRaw = cell(row, columns.get("status"), formatter);
                String status = DomainConstants.STATUS_SCREENING;
                if (!statusRaw.isBlank()) {
                    String parsed = parseStatus(statusRaw);
                    if (parsed == null) {
                        skipped++;
                        addError(errors, displayRow, "无法识别的状态: " + statusRaw);
                        continue;
                    }
                    status = parsed;
                }

                ApplicationRecord record = new ApplicationRecord();
                record.setCategory(category);
                record.setCompanyName(company);
                record.setLink(link);
                record.setApplyYear(recordYear);
                record.setPosition(trimToNull(cell(row, columns.get("position"), formatter)));
                record.setStatus(status);
                record.setChannel(trimToNull(cell(row, columns.get("channel"), formatter)));
                record.setCity(trimToNull(cell(row, columns.get("city"), formatter)));
                record.setSalary(trimToNull(cell(row, columns.get("salary"), formatter)));
                record.setPriority(trimToNull(cell(row, columns.get("priority"), formatter)));
                record.setNotes(trimToNull(cell(row, columns.get("notes"), formatter)));
                record.setPinned(false);
                record.setAppliedAt(appliedAt);

                applicationMapper.insert(record);
                historyService.recordStatusChange(record.getId(), DomainConstants.STATUS_PENDING, appliedAt);
                historyService.recordStatusChange(record.getId(), status, appliedAt);
                imported++;
            }

            applicationCache.invalidate();
            return ImportResult.of(imported, skipped, errors);
        } catch (IOException | RuntimeException exception) {
            if (exception instanceof BusinessException businessException) {
                throw businessException;
            }
            throw BusinessException.badRequest("invalid_excel", "无法解析 Excel 文件");
        }
    }

    private int findHeaderRow(Sheet sheet, DataFormatter formatter) {
        int limit = Math.min(sheet.getLastRowNum(), 10);
        for (int index = 0; index <= limit; index++) {
            Row row = sheet.getRow(index);
            if (row == null) {
                continue;
            }
            Map<String, Integer> columns = mapColumns(row, formatter);
            if (columns.containsKey("company")) {
                return index;
            }
        }
        return -1;
    }

    private Map<String, Integer> mapColumns(Row row, DataFormatter formatter) {
        Map<String, Integer> columns = new HashMap<>();
        if (row == null) {
            return columns;
        }
        for (int index = 0; index < row.getLastCellNum(); index++) {
            String header = normalize(cell(row, index, formatter));
            if (header.isEmpty()) {
                continue;
            }
            if (!columns.containsKey("company") && matches(header,
                    "company", "companyname", "企业名称", "公司名称", "企业", "公司", "会社名", "기업명", "회사명")) {
                columns.put("company", index);
            } else if (!columns.containsKey("link") && matches(header,
                    "link", "url", "applicationlink", "投递链接", "投遞連結", "链接", "連結", "网址", "網址", "応募リンク", "지원링크")) {
                columns.put("link", index);
            } else if (!columns.containsKey("year") && matches(header,
                    "year", "applyyear", "年度", "年份", "応募年度", "지원연도")) {
                columns.put("year", index);
            } else if (!columns.containsKey("category") && matches(header,
                    "category", "投递类型", "投遞類型", "大分类", "分类", "类别", "招聘类型", "招聘類型", "採用区分", "채용유형")) {
                columns.put("category", index);
            } else if (!columns.containsKey("position") && matches(header,
                    "position", "job", "title", "岗位", "職位", "职位", "応募職種", "포지션")) {
                columns.put("position", index);
            } else if (!columns.containsKey("status") && matches(header,
                    "status", "状态", "狀態", "进度", "進度", "進捗", "진행상태")) {
                columns.put("status", index);
            } else if (!columns.containsKey("channel") && matches(header,
                    "channel", "source", "渠道", "来源", "來源", "経路", "채널")) {
                columns.put("channel", index);
            } else if (!columns.containsKey("city") && matches(header,
                    "city", "location", "城市", "地点", "地點", "勤務地", "도시")) {
                columns.put("city", index);
            } else if (!columns.containsKey("salary") && matches(header,
                    "salary", "薪资", "薪資", "年収", "연봉")) {
                columns.put("salary", index);
            } else if (!columns.containsKey("priority") && matches(header,
                    "priority", "优先级", "優先度", "우선순위")) {
                columns.put("priority", index);
            } else if (!columns.containsKey("notes") && matches(header,
                    "notes", "note", "remark", "备注", "備註", "メモ", "비고")) {
                columns.put("notes", index);
            }
        }
        return columns;
    }

    private boolean matches(String header, String... aliases) {
        for (String alias : aliases) {
            if (header.contains(normalize(alias))) {
                return true;
            }
        }
        return false;
    }

    private String normalize(String value) {
        if (value == null) {
            return "";
        }
        return value.toLowerCase(Locale.ROOT)
                .replaceAll("[\\s/\\\\\\-_\\(\\)（）\\[\\]、|:：.]+", "");
    }

    private String parseCategory(String value) {
        String key = normalize(value);
        String direct = CATEGORY_ALIASES.get(key);
        if (direct != null) {
            return direct;
        }
        for (Map.Entry<String, String> entry : CATEGORY_ALIASES.entrySet()) {
            if (key.startsWith(normalize(entry.getKey()))) {
                return entry.getValue();
            }
        }
        return null;
    }

    private String parseStatus(String value) {
        String key = normalize(value);
        String direct = STATUS_ALIASES.get(key);
        if (direct != null) {
            return direct;
        }
        for (Map.Entry<String, String> entry : STATUS_ALIASES.entrySet()) {
            if (key.startsWith(normalize(entry.getKey()))) {
                return entry.getValue();
            }
        }
        return null;
    }

    private String cell(Row row, Integer index, DataFormatter formatter) {
        if (index == null || row == null) {
            return "";
        }
        Cell cell = row.getCell(index);
        return cell == null ? "" : formatter.formatCellValue(cell).trim();
    }

    private boolean rowIsEmpty(Row row, DataFormatter formatter) {
        for (int index = 0; index < row.getLastCellNum(); index++) {
            if (!cell(row, index, formatter).isBlank()) {
                return false;
            }
        }
        return true;
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private void addError(List<String> errors, int row, String message) {
        if (errors.size() < 20) {
            errors.add("第 " + row + " 行：" + message);
        }
    }
}
