package com.paulfernandosr.possystembackend.product.application.reference;

import com.paulfernandosr.possystembackend.product.domain.Product;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.web.server.ResponseStatusException;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR;

public final class ProductReferenceInfoExcelGenerator {

    private static final List<String> HEADERS = List.of(
            "CODIGO",
            "DESCRIPCION",
            "MARCA",
            "PROCEDENCIA",
            "PRESENTACION",
            "A",
            "B",
            "C",
            "D"
    );

    private ProductReferenceInfoExcelGenerator() {
    }

    public static byte[] generate(List<String> requestedSkus, Map<String, Product> productsBySku) {
        try (Workbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = wb.createSheet("PRODUCTOS");
            CellStyle headerStyle = headerStyle(wb);
            CellStyle moneyStyle = moneyStyle(wb);
            CellStyle missingStyle = missingStyle(wb);

            Row header = sheet.createRow(0);
            header.setHeightInPoints(22);
            for (int c = 0; c < HEADERS.size(); c++) {
                Cell cell = header.createCell(c);
                cell.setCellValue(HEADERS.get(c));
                cell.setCellStyle(headerStyle);
            }

            int rowIndex = 1;
            for (String requestedSku : requestedSkus) {
                Product p = productsBySku.get(normalizeKey(requestedSku));
                Row row = sheet.createRow(rowIndex++);

                if (p == null) {
                    row.createCell(0).setCellValue(requestedSku);
                    Cell missing = row.createCell(1);
                    missing.setCellValue("NO ENCONTRADO");
                    missing.setCellStyle(missingStyle);
                    continue;
                }

                row.createCell(0).setCellValue(nullToBlank(p.getSku()));
                row.createCell(1).setCellValue(nullToBlank(p.getName()));
                row.createCell(2).setCellValue(nullToBlank(p.getBrand()));
                row.createCell(3).setCellValue(nullToBlank(p.getOriginCountry()));
                row.createCell(4).setCellValue(nullToBlank(p.getPresentation()));
                writeMoney(row, 5, p.getPriceA(), moneyStyle);
                writeMoney(row, 6, p.getPriceB(), moneyStyle);
                writeMoney(row, 7, p.getPriceC(), moneyStyle);
                writeMoney(row, 8, p.getPriceD(), moneyStyle);
            }

            sheet.createFreezePane(0, 1);
            int[] widths = {16, 48, 22, 20, 24, 14, 14, 14, 14};
            for (int c = 0; c < widths.length; c++) sheet.setColumnWidth(c, widths[c] * 256);
            sheet.setAutoFilter(new org.apache.poi.ss.util.CellRangeAddress(0, Math.max(1, rowIndex - 1), 0, HEADERS.size() - 1));

            wb.write(out);
            return out.toByteArray();
        } catch (Exception e) {
            throw new ResponseStatusException(INTERNAL_SERVER_ERROR, "No se pudo generar el Excel referencial de productos.");
        }
    }

    private static void writeMoney(Row row, int col, BigDecimal value, CellStyle style) {
        Cell cell = row.createCell(col);
        if (value != null) cell.setCellValue(value.doubleValue());
        cell.setCellStyle(style);
    }

    private static String normalizeKey(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }

    private static String nullToBlank(String value) {
        return value == null ? "" : value;
    }

    private static CellStyle headerStyle(Workbook wb) {
        Font font = wb.createFont();
        font.setBold(true);
        font.setColor(IndexedColors.WHITE.getIndex());
        CellStyle style = wb.createCellStyle();
        style.setFont(font);
        style.setFillForegroundColor(IndexedColors.DARK_BLUE.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        style.setAlignment(HorizontalAlignment.CENTER);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        return style;
    }

    private static CellStyle moneyStyle(Workbook wb) {
        CellStyle style = wb.createCellStyle();
        style.setDataFormat(wb.getCreationHelper().createDataFormat().getFormat("#,##0.00"));
        return style;
    }

    private static CellStyle missingStyle(Workbook wb) {
        Font font = wb.createFont();
        font.setBold(true);
        font.setColor(IndexedColors.RED.getIndex());
        CellStyle style = wb.createCellStyle();
        style.setFont(font);
        return style;
    }
}
