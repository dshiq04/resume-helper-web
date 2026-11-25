package com.jobtrack.application.excel;

import org.apache.fesod.sheet.converters.Converter;
import org.apache.fesod.sheet.converters.WriteConverterContext;
import org.apache.fesod.sheet.enums.CellDataTypeEnum;
import org.apache.fesod.sheet.metadata.data.HyperlinkData;
import org.apache.fesod.sheet.metadata.data.WriteCellData;
import org.apache.fesod.sheet.write.metadata.style.WriteCellStyle;
import org.apache.fesod.sheet.write.metadata.style.WriteFont;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.IndexedColors;

import java.net.URI;
import java.util.Locale;

/**
 * 导出「投递链接」时，把 http/https 地址写成 Excel 可点击的超链接，
 * 并套用蓝色下划线样式；不是合法链接的文本仍按普通字符串输出。
 */
public class HyperlinkStringConverter implements Converter<String> {

    @Override
    public Class<?> supportJavaTypeKey() {
        return String.class;
    }

    @Override
    public CellDataTypeEnum supportExcelTypeKey() {
        return CellDataTypeEnum.STRING;
    }

    @Override
    public WriteCellData<?> convertToExcelData(WriteConverterContext<String> context) {
        String value = context.getValue() == null ? "" : context.getValue().trim();
        WriteCellData<String> cellData = new WriteCellData<>(value);
        String address = toHyperlinkAddress(value);
        if (address == null) {
            return cellData;
        }

        HyperlinkData hyperlink = new HyperlinkData();
        hyperlink.setAddress(address);
        hyperlink.setHyperlinkType(HyperlinkData.HyperlinkType.URL);
        cellData.setHyperlinkData(hyperlink);

        WriteFont font = new WriteFont();
        font.setColor(IndexedColors.BLUE.getIndex());
        font.setUnderline(Font.U_SINGLE);
        WriteCellStyle style = new WriteCellStyle();
        style.setWriteFont(font);
        cellData.setWriteCellStyle(style);
        return cellData;
    }

    /**
     * 仅接受 http/https 链接；以 www. 开头的地址补全协议。
     * 其他内容（岗位描述、内推码等）保持普通文本，避免 Excel 生成可执行链接。
     */
    private String toHyperlinkAddress(String value) {
        if (value.isEmpty()) {
            return null;
        }
        String candidate = value.toLowerCase(Locale.ROOT).startsWith("www.")
                ? "https://" + value : value;
        try {
            URI uri = URI.create(candidate);
            String scheme = uri.getScheme();
            if (scheme == null
                    || !("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme))
                    || uri.getHost() == null || uri.getHost().isBlank()) {
                return null;
            }
            return candidate;
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }
}
