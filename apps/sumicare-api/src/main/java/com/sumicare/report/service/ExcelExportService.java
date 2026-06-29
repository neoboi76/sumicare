/*
 * Developed by the following authors:
 *     Lance Gabriel C. De La Paz (lgcdelapaz@mymail.mapua.edu.ph)
 *     Franz C. Pereira (fcpereira@mymail.mapua.edu.ph)
 *     Dino Alfred T. Timbol (dattimbol@mymail.mapua.edu.ph)
 */

package com.sumicare.report.service;

import com.sumicare.common.util.LogoResolver;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.*;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
public class ExcelExportService {

    private static final ZoneId MANILA = ZoneId.of("Asia/Manila");
    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("MMM d, yyyy h:mm a");

    private final LogoResolver logoResolver;

    public ExcelExportService(LogoResolver logoResolver) {
        this.logoResolver = logoResolver;
    }

    public static class WorkbookContext {
        public final XSSFWorkbook workbook;
        public final XSSFSheet sheet;
        public final CellStyle headerStyle;
        public final CellStyle evenRowStyle;
        public final CellStyle oddRowStyle;
        public final CellStyle totalStyle;
        public final CellStyle moneyStyle;
        public int currentRow;

        WorkbookContext(XSSFWorkbook workbook, XSSFSheet sheet, CellStyle headerStyle,
                        CellStyle evenRowStyle, CellStyle oddRowStyle,
                        CellStyle totalStyle, CellStyle moneyStyle) {
            this.workbook = workbook;
            this.sheet = sheet;
            this.headerStyle = headerStyle;
            this.evenRowStyle = evenRowStyle;
            this.oddRowStyle = oddRowStyle;
            this.totalStyle = totalStyle;
            this.moneyStyle = moneyStyle;
            this.currentRow = 0;
        }
    }

    public WorkbookContext createWorkbook(String sheetName, String reportTitle, String dateRange,
                                          String preparedBy, String logoUrl) {
        XSSFWorkbook workbook = new XSSFWorkbook();
        XSSFSheet sheet = workbook.createSheet(sheetName);
        sheet.setDefaultColumnWidth(18);

        CellStyle titleStyle = workbook.createCellStyle();
        Font titleFont = workbook.createFont();
        titleFont.setBold(true);
        titleFont.setFontHeightInPoints((short) 14);
        titleStyle.setFont(titleFont);

        CellStyle labelStyle = workbook.createCellStyle();
        Font labelFont = workbook.createFont();
        labelFont.setColor(IndexedColors.GREY_50_PERCENT.getIndex());
        labelStyle.setFont(labelFont);

        CellStyle headerStyle = workbook.createCellStyle();
        Font headerFont = workbook.createFont();
        headerFont.setBold(true);
        headerFont.setColor(IndexedColors.WHITE.getIndex());
        headerStyle.setFont(headerFont);
        headerStyle.setFillForegroundColor(new XSSFColor(new byte[]{(byte) 0x1e, (byte) 0x40, (byte) 0x6e}, null));
        headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        headerStyle.setBorderBottom(BorderStyle.THIN);
        headerStyle.setBottomBorderColor(IndexedColors.GREY_25_PERCENT.getIndex());
        headerStyle.setWrapText(false);

        XSSFColor evenBg = new XSSFColor(new byte[]{(byte) 0xf8, (byte) 0xfa, (byte) 0xfc}, null);
        XSSFColor oddBg = new XSSFColor(new byte[]{(byte) 0xff, (byte) 0xff, (byte) 0xff}, null);

        CellStyle evenRowStyle = workbook.createCellStyle();
        ((XSSFCellStyle) evenRowStyle).setFillForegroundColor(evenBg);
        evenRowStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        evenRowStyle.setBorderBottom(BorderStyle.THIN);
        evenRowStyle.setBottomBorderColor(IndexedColors.GREY_25_PERCENT.getIndex());

        CellStyle oddRowStyle = workbook.createCellStyle();
        ((XSSFCellStyle) oddRowStyle).setFillForegroundColor(oddBg);
        oddRowStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        oddRowStyle.setBorderBottom(BorderStyle.THIN);
        oddRowStyle.setBottomBorderColor(IndexedColors.GREY_25_PERCENT.getIndex());

        CellStyle totalStyle = workbook.createCellStyle();
        Font totalFont = workbook.createFont();
        totalFont.setBold(true);
        totalStyle.setFont(totalFont);
        totalStyle.setFillForegroundColor(new XSSFColor(new byte[]{(byte) 0xe2, (byte) 0xe8, (byte) 0xf0}, null));
        totalStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        totalStyle.setBorderTop(BorderStyle.MEDIUM);
        totalStyle.setTopBorderColor(IndexedColors.GREY_50_PERCENT.getIndex());

        CellStyle moneyStyle = workbook.createCellStyle();
        DataFormat format = workbook.createDataFormat();
        moneyStyle.cloneStyleFrom(evenRowStyle);
        moneyStyle.setDataFormat(format.getFormat("#,##0.00"));

        WorkbookContext ctx = new WorkbookContext(workbook, sheet, headerStyle,
                evenRowStyle, oddRowStyle, totalStyle, moneyStyle);

        int row = 0;

        String logoData = logoResolver.dataUriOrNull(logoUrl);
        if (logoData != null && logoData.startsWith("data:image/")) {
            try {
                String base64Part = logoData.substring(logoData.indexOf(',') + 1);
                byte[] imageBytes = java.util.Base64.getDecoder().decode(base64Part);
                String mimeType = logoData.substring(5, logoData.indexOf(';'));
                int pictureType = mimeType.contains("png") ? XSSFWorkbook.PICTURE_TYPE_PNG
                        : XSSFWorkbook.PICTURE_TYPE_JPEG;
                int pictureIdx = workbook.addPicture(imageBytes, pictureType);
                CreationHelper helper = workbook.getCreationHelper();
                Drawing<?> drawing = sheet.createDrawingPatriarch();
                ClientAnchor anchor = helper.createClientAnchor();
                anchor.setCol1(0);
                anchor.setRow1(row);
                anchor.setCol2(2);
                anchor.setRow2(row + 3);
                drawing.createPicture(anchor, pictureIdx);
                row += 3;
            } catch (Exception ignored) {}
        }

        writeCell(sheet, row, 0, reportTitle, titleStyle);
        row++;
        writeCell(sheet, row, 0, dateRange, labelStyle);
        row++;
        writeCell(sheet, row, 0, "Prepared by: " + preparedBy, labelStyle);
        row++;
        String generated = OffsetDateTime.now().atZoneSameInstant(MANILA).format(STAMP);
        writeCell(sheet, row, 0, "Generated: " + generated + " (Manila)", labelStyle);
        row++;
        row++;

        ctx.currentRow = row;
        return ctx;
    }

    public void writeHeaderRow(WorkbookContext ctx, List<String> headers) {
        Row row = ctx.sheet.createRow(ctx.currentRow++);
        for (int i = 0; i < headers.size(); i++) {
            Cell cell = row.createCell(i);
            cell.setCellValue(headers.get(i));
            cell.setCellStyle(ctx.headerStyle);
        }
    }

    public void writeDataRow(WorkbookContext ctx, List<Object> values) {
        boolean even = ctx.currentRow % 2 == 0;
        Row row = ctx.sheet.createRow(ctx.currentRow++);
        for (int i = 0; i < values.size(); i++) {
            Cell cell = row.createCell(i);
            Object v = values.get(i);
            if (v instanceof Number n) {
                cell.setCellValue(n.doubleValue());
                cell.setCellStyle(even ? ctx.evenRowStyle : ctx.oddRowStyle);
            } else {
                cell.setCellValue(v != null ? v.toString() : "");
                cell.setCellStyle(even ? ctx.evenRowStyle : ctx.oddRowStyle);
            }
        }
    }

    public void writeMoneyRow(WorkbookContext ctx, List<Object> values, int moneyColumnIndex) {
        boolean even = ctx.currentRow % 2 == 0;
        Row row = ctx.sheet.createRow(ctx.currentRow++);
        for (int i = 0; i < values.size(); i++) {
            Cell cell = row.createCell(i);
            Object v = values.get(i);
            if (v instanceof Number n && i == moneyColumnIndex) {
                cell.setCellValue(n.doubleValue());
                cell.setCellStyle(ctx.moneyStyle);
            } else if (v instanceof Number n) {
                cell.setCellValue(n.doubleValue());
                cell.setCellStyle(even ? ctx.evenRowStyle : ctx.oddRowStyle);
            } else {
                cell.setCellValue(v != null ? v.toString() : "");
                cell.setCellStyle(even ? ctx.evenRowStyle : ctx.oddRowStyle);
            }
        }
    }

    public void writeTotalRow(WorkbookContext ctx, List<Object> values) {
        Row row = ctx.sheet.createRow(ctx.currentRow++);
        for (int i = 0; i < values.size(); i++) {
            Cell cell = row.createCell(i);
            Object v = values.get(i);
            if (v instanceof Number n) {
                cell.setCellValue(n.doubleValue());
            } else {
                cell.setCellValue(v != null ? v.toString() : "");
            }
            cell.setCellStyle(ctx.totalStyle);
        }
    }

    public void writeBlankRow(WorkbookContext ctx) {
        ctx.sheet.createRow(ctx.currentRow++);
    }

    public void writeNarrativeSection(WorkbookContext ctx, String sectionTitle, String narrative, int columnSpan) {
        ctx.currentRow++;
        XSSFCellStyle sectionTitleStyle = (XSSFCellStyle) ctx.workbook.createCellStyle();
        Font sectionFont = ctx.workbook.createFont();
        sectionFont.setBold(true);
        sectionTitleStyle.setFont(sectionFont);

        Row titleRow = ctx.sheet.createRow(ctx.currentRow++);
        Cell titleCell = titleRow.createCell(0);
        titleCell.setCellValue(sectionTitle);
        titleCell.setCellStyle(sectionTitleStyle);
        ctx.sheet.addMergedRegion(new CellRangeAddress(titleRow.getRowNum(), titleRow.getRowNum(), 0, columnSpan - 1));

        CellStyle narrativeStyle = ctx.workbook.createCellStyle();
        narrativeStyle.setWrapText(true);

        Row narrativeRow = ctx.sheet.createRow(ctx.currentRow++);
        narrativeRow.setHeightInPoints(80);
        Cell narrativeCell = narrativeRow.createCell(0);
        narrativeCell.setCellValue(narrative != null ? narrative : "");
        narrativeCell.setCellStyle(narrativeStyle);
        ctx.sheet.addMergedRegion(
                new CellRangeAddress(narrativeRow.getRowNum(), narrativeRow.getRowNum(), 0, columnSpan - 1));
    }

    public void writeFooter(WorkbookContext ctx, int columnSpan) {
        ctx.currentRow++;
        CellStyle footerStyle = ctx.workbook.createCellStyle();
        Font footerFont = ctx.workbook.createFont();
        footerFont.setItalic(true);
        footerFont.setColor(IndexedColors.GREY_50_PERCENT.getIndex());
        footerStyle.setFont(footerFont);

        Row footerRow = ctx.sheet.createRow(ctx.currentRow++);
        Cell footerCell = footerRow.createCell(0);
        footerCell.setCellValue("Powered by SumiCare");
        footerCell.setCellStyle(footerStyle);
        ctx.sheet.addMergedRegion(
                new CellRangeAddress(footerRow.getRowNum(), footerRow.getRowNum(), 0, columnSpan - 1));
    }

    public void autoSizeColumns(WorkbookContext ctx, int columnCount) {
        for (int i = 0; i < columnCount; i++) {
            ctx.sheet.autoSizeColumn(i);
            int width = ctx.sheet.getColumnWidth(i);
            ctx.sheet.setColumnWidth(i, Math.min(width + 512, 15000));
        }
    }

    public byte[] toBytes(XSSFWorkbook workbook) {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            workbook.write(out);
            workbook.close();
            return out.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException("Failed to write Excel workbook", e);
        }
    }

    private void writeCell(Sheet sheet, int rowIndex, int colIndex, String value, CellStyle style) {
        Row row = sheet.getRow(rowIndex);
        if (row == null) row = sheet.createRow(rowIndex);
        Cell cell = row.createCell(colIndex);
        cell.setCellValue(value != null ? value : "");
        if (style != null) cell.setCellStyle(style);
    }
}
