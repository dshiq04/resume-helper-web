package com.jobtrack.application.pojo.dto;

import com.jobtrack.application.excel.HyperlinkStringConverter;
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
 * Excel 导出行：年份 / 投递类型 / 公司名称 / 投递链接 / 投递时间。
 * 列顺序与表头即导出文件的最终展示内容。
 */
@Data
@HeadRowHeight(30)
@ContentRowHeight(22)
@HeadFontStyle(fontHeightInPoints = 12, bold = BooleanEnum.TRUE)
@ContentFontStyle(fontHeightInPoints = 11)
public class ApplicationExportDTO {

    @ExcelProperty("年份")
    @ColumnWidth(10)
    @HeadStyle(horizontalAlignment = HorizontalAlignmentEnum.CENTER,
            verticalAlignment = VerticalAlignmentEnum.CENTER)
    private Integer year;

    @ExcelProperty("投递类型")
    @ColumnWidth(14)
    @HeadStyle(horizontalAlignment = HorizontalAlignmentEnum.CENTER,
            verticalAlignment = VerticalAlignmentEnum.CENTER)
    private String category;

    @ExcelProperty("公司名称")
    @ColumnWidth(26)
    @HeadStyle(horizontalAlignment = HorizontalAlignmentEnum.CENTER,
            verticalAlignment = VerticalAlignmentEnum.CENTER)
    private String company;

    @ExcelProperty(value = "投递链接", converter = HyperlinkStringConverter.class)
    @ColumnWidth(46)
    @HeadStyle(horizontalAlignment = HorizontalAlignmentEnum.CENTER,
            verticalAlignment = VerticalAlignmentEnum.CENTER)
    private String link;

    @ExcelProperty("投递时间")
    @ColumnWidth(20)
    @HeadStyle(horizontalAlignment = HorizontalAlignmentEnum.CENTER,
            verticalAlignment = VerticalAlignmentEnum.CENTER)
    private String time;
}
