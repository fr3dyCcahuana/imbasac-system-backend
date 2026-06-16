package com.paulfernandosr.possystembackend.product.application.reference;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.web.server.ResponseStatusException;

import java.io.ByteArrayOutputStream;
import java.util.List;

import static org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR;

public final class ProductReferenceInfoTemplateGenerator {

    private static final List<String> HEADERS = List.of("ORDEN", "SKU");
    private static final int FIRST_DATA_ROW = 1;
    private static final int LAST_DATA_ROW = 1000;

    private ProductReferenceInfoTemplateGenerator() {
    }

    public static byte[] generate() {
        try (Workbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = wb.createSheet("CODIGOS");
            Sheet readme = wb.createSheet("README");

            CellStyle headerStyle = headerStyle(wb);
            CellStyle textStyle = textStyle(wb);
            CellStyle integerStyle = integerStyle(wb);
            CellStyle readmeTitleStyle = readmeTitleStyle(wb);
            CellStyle readmeBodyStyle = readmeBodyStyle(wb);

            writeCodeSheet(sheet, headerStyle, integerStyle, textStyle);
            writeReadmeSheet(readme, readmeTitleStyle, readmeBodyStyle);

            wb.write(out);
            return out.toByteArray();
        } catch (Exception e) {
            throw new ResponseStatusException(INTERNAL_SERVER_ERROR, "No se pudo generar la plantilla de codigos.");
        }
    }

    private static void writeCodeSheet(Sheet sheet, CellStyle headerStyle, CellStyle integerStyle, CellStyle textStyle) {
        Row header = sheet.createRow(0);
        header.setHeightInPoints(22);
        for (int c = 0; c < HEADERS.size(); c++) {
            Cell cell = header.createCell(c);
            cell.setCellValue(HEADERS.get(c));
            cell.setCellStyle(headerStyle);
        }

        for (int r = FIRST_DATA_ROW; r <= LAST_DATA_ROW; r++) {
            Row row = sheet.createRow(r);
            row.createCell(0).setCellStyle(integerStyle);
            row.createCell(1).setCellStyle(textStyle);
        }

        sheet.setColumnWidth(0, 14 * 256);
        sheet.setColumnWidth(1, 22 * 256);
        sheet.createFreezePane(0, 1);
        sheet.setAutoFilter(new org.apache.poi.ss.util.CellRangeAddress(0, LAST_DATA_ROW, 0, HEADERS.size() - 1));
    }

    private static void writeReadmeSheet(Sheet sheet, CellStyle titleStyle, CellStyle bodyStyle) {
        String[] lines = {
                "Plantilla para obtener informacion referencial de productos",
                "",
                "Use la hoja CODIGOS.",
                "Complete la columna SKU con los codigos de producto.",
                "La columna ORDEN es opcional y sirve para mantener una referencia visual.",
                "",
                "Luego cargue este archivo en Productos > Info referencial.",
                "El sistema devolvera un Excel con:",
                "CODIGO, DESCRIPCION, MARCA, PROCEDENCIA, PRESENTACION, A, B, C y D.",
                "",
                "Si un SKU no existe, aparecera como NO ENCONTRADO."
        };

        for (int r = 0; r < lines.length; r++) {
            Row row = sheet.createRow(r);
            Cell cell = row.createCell(0);
            cell.setCellValue(lines[r]);
            cell.setCellStyle(r == 0 ? titleStyle : bodyStyle);
        }
        sheet.setColumnWidth(0, 95 * 256);
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

    private static CellStyle textStyle(Workbook wb) {
        CellStyle style = wb.createCellStyle();
        style.setDataFormat(wb.getCreationHelper().createDataFormat().getFormat("@"));
        return style;
    }

    private static CellStyle integerStyle(Workbook wb) {
        CellStyle style = wb.createCellStyle();
        style.setDataFormat(wb.getCreationHelper().createDataFormat().getFormat("0"));
        return style;
    }

    private static CellStyle readmeTitleStyle(Workbook wb) {
        Font font = wb.createFont();
        font.setBold(true);
        font.setFontHeightInPoints((short) 14);
        CellStyle style = wb.createCellStyle();
        style.setFont(font);
        return style;
    }

    private static CellStyle readmeBodyStyle(Workbook wb) {
        CellStyle style = wb.createCellStyle();
        style.setWrapText(true);
        return style;
    }
}
