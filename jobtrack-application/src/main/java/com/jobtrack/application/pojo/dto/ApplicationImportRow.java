package com.jobtrack.application.pojo.dto;

import lombok.Data;
import org.apache.fesod.sheet.annotation.ExcelProperty;

/**
 * Excel 导入的原始行：所有列先按字符串读取，再逐行转换与校验。
 *
 * <p>这样即使某个单元格类型不正确（例如「年度」填了文本），也只影响该行，
 * 可以在导入结果里返回「第 N 行：年度必须为数字」，而不是整份文件失败。</p>
 */
@Data
public class ApplicationImportRow {

    @ExcelProperty("年度")
    private String year;

    @ExcelProperty("投递类型")
    private String category;

    @ExcelProperty("公司")
    private String company;

    @ExcelProperty("投递地址")
    private String link;

    @ExcelProperty("时间")
    private String time;
}
