package com.chatopera.cc.util.excel;

import org.apache.poi.ss.usermodel.*;

public class ExcelBase {
    protected Workbook wb;

    protected CellStyle baseCellStyle() {
        CellStyle cellStyle = wb.createCellStyle();
        baseStyle(cellStyle);

        cellStyle.setWrapText(true); // 指定单元格自动换行
        // 设置单元格字体
        Font font = wb.createFont();
        font.setBold(true);
        font.setFontName("宋体");
        font.setFontHeight((short) 200);
        cellStyle.setFont(font);

        return cellStyle;
    }

    protected void baseStyle(CellStyle cellStyle) {
        cellStyle.setAlignment(HorizontalAlignment.CENTER); // 指定单元格居中对齐
        cellStyle.setVerticalAlignment(VerticalAlignment.CENTER); // 指定单元格垂直居中对齐
        cellStyle.setBorderTop(BorderStyle.THIN);
        cellStyle.setBorderLeft(BorderStyle.THIN);
        cellStyle.setBorderRight(BorderStyle.THIN);
        cellStyle.setBorderBottom(BorderStyle.THIN);
    }
}
