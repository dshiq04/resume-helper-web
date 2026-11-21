package com.jobtrack.application.dto;

import lombok.Data;
import org.apache.fesod.sheet.annotation.ExcelProperty;

/**
 * Excel 导入行：年度 / 投递类型 / 公司 / 投递地址 / 时间。
 */
@Data
public class ApplicationImportRow {

    @ExcelProperty("年度")
    private Integer year;

    @ExcelProperty("投递类型")
    private String category;

    @ExcelProperty("公司")
    private String company;

    @ExcelProperty("投递地址")
    private String link;

    @ExcelProperty("时间")
    private String time;
}
