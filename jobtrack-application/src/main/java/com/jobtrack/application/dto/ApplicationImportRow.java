package com.jobtrack.application.dto;

import lombok.Data;
import org.apache.fesod.sheet.annotation.ExcelProperty;
import org.apache.fesod.sheet.annotation.write.style.ColumnWidth;
import org.apache.fesod.sheet.annotation.write.style.ContentFontStyle;
import org.apache.fesod.sheet.annotation.write.style.ContentRowHeight;
import org.apache.fesod.sheet.annotation.write.style.HeadFontStyle;
import org.apache.fesod.sheet.annotation.write.style.HeadRowHeight;
import org.apache.fesod.sheet.annotation.write.style.HeadStyle;
import org.apache.fesod.sheet.enums.BooleanEnum;
import org.apache.fesod.sheet.enums.poi.HorizontalAlignmentEnum;
import org.apache.fesod.sheet.enums.poi.VerticalAlignmentEnum;

/**
 * Excel 导入行：年度 / 投递类型 / 公司 / 投递地址 / 时间。
 * 样式注解同时用于生成导入模板。
 */
@Data
@HeadRowHeight(30)
@ContentRowHeight(22)
@HeadFontStyle(fontHeightInPoints = 12, bold = BooleanEnum.TRUE)
@ContentFontStyle(fontHeightInPoints = 11)
public class ApplicationImportRow {

    @ExcelProperty("年度")
    @ColumnWidth(10)
    @HeadStyle(horizontalAlignment = HorizontalAlignmentEnum.CENTER,
            verticalAlignment = VerticalAlignmentEnum.CENTER)
    private Integer year;

    @ExcelProperty("投递类型")
    @ColumnWidth(14)
    @HeadStyle(horizontalAlignment = HorizontalAlignmentEnum.CENTER,
            verticalAlignment = VerticalAlignmentEnum.CENTER)
    private String category;

    @ExcelProperty("公司")
    @ColumnWidth(26)
    @HeadStyle(horizontalAlignment = HorizontalAlignmentEnum.CENTER,
            verticalAlignment = VerticalAlignmentEnum.CENTER)
    private String company;

    @ExcelProperty("投递地址")
    @ColumnWidth(46)
    @HeadStyle(horizontalAlignment = HorizontalAlignmentEnum.CENTER,
            verticalAlignment = VerticalAlignmentEnum.CENTER)
    private String link;

    @ExcelProperty("时间")
    @ColumnWidth(20)
    @HeadStyle(horizontalAlignment = HorizontalAlignmentEnum.CENTER,
            verticalAlignment = VerticalAlignmentEnum.CENTER)
    private String time;
}
