package com.jobtrack.application.excel;

import com.jobtrack.application.pojo.dto.ApplicationExportDTO;
import org.apache.fesod.sheet.FesodSheet;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Hyperlink;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * 导出的「投递链接」列：合法 URL 应写成可点击超链接，普通文本保持原样。
 */
class ApplicationExportLinkTest {

    @Test
    void writesClickableHyperlinkOnlyForWebLinks() throws Exception {
        ApplicationExportDTO web = row("https://jobs.example.com/apply?id=1");
        ApplicationExportDTO www = row("www.example.com/jobs");
        ApplicationExportDTO text = row("内推码：ABC123");

        byte[] content;
        try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            FesodSheet.write(output, ApplicationExportDTO.class)
                    .sheet("投递台账")
                    .doWrite(List.of(web, www, text));
            content = output.toByteArray();
        }

        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(content))) {
            Sheet sheet = workbook.getSheetAt(0);
            Cell linkCell = sheet.getRow(1).getCell(3);
            assertEquals("https://jobs.example.com/apply?id=1", hyperlinkAddress(linkCell));
            Font font = workbook.getFontAt(linkCell.getCellStyle().getFontIndex());
            assertEquals(IndexedColors.BLUE.getIndex(), font.getColor());
            assertEquals(Font.U_SINGLE, font.getUnderline());
            assertEquals("https://www.example.com/jobs",
                    hyperlinkAddress(sheet.getRow(2).getCell(3)));
            assertNull(sheet.getRow(3).getCell(3).getHyperlink());
            assertEquals("内推码：ABC123", sheet.getRow(3).getCell(3).getStringCellValue());
        }
    }

    private ApplicationExportDTO row(String link) {
        ApplicationExportDTO row = new ApplicationExportDTO();
        row.setYear(2026);
        row.setCategory("秋招");
        row.setCompany("示例公司");
        row.setLink(link);
        row.setTime("2026-09-01 10:00");
        return row;
    }

    private String hyperlinkAddress(Cell cell) {
        Hyperlink hyperlink = cell.getHyperlink();
        assertNotNull(hyperlink);
        return hyperlink.getAddress();
    }
}
