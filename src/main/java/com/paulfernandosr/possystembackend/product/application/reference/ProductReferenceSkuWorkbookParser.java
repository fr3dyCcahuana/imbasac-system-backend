package com.paulfernandosr.possystembackend.product.application.reference;

import org.apache.poi.ss.usermodel.*;
import org.springframework.web.server.ResponseStatusException;

import java.io.ByteArrayInputStream;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static org.springframework.http.HttpStatus.BAD_REQUEST;

public final class ProductReferenceSkuWorkbookParser {

    private ProductReferenceSkuWorkbookParser() {
    }

    public static List<String> parseSkus(byte[] fileBytes) {
        try (Workbook wb = WorkbookFactory.create(new ByteArrayInputStream(fileBytes))) {
            Sheet sheet = wb.getNumberOfSheets() > 0 ? wb.getSheetAt(0) : null;
            if (sheet == null) {
                throw new ResponseStatusException(BAD_REQUEST, "El Excel no contiene hojas.");
            }

            DataFormatter formatter = new DataFormatter(Locale.US);
            int headerRowIndex = findHeaderRow(sheet, formatter);
            int skuColumn = findSkuColumn(sheet.getRow(headerRowIndex), formatter);
            if (skuColumn < 0) {
                throw new ResponseStatusException(BAD_REQUEST, "No se encontro una columna SKU, CODIGO o CODIGO (SKU).");
            }

            List<String> skus = new ArrayList<>();
            for (int r = headerRowIndex + 1; r <= sheet.getLastRowNum(); r++) {
                Row row = sheet.getRow(r);
                if (row == null) continue;
                String sku = normalizeValue(formatter.formatCellValue(row.getCell(skuColumn)));
                if (sku != null) skus.add(sku);
            }

            if (skus.isEmpty()) {
                throw new ResponseStatusException(BAD_REQUEST, "El Excel no contiene SKUs para consultar.");
            }

            return skus;
        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            throw new ResponseStatusException(BAD_REQUEST, "El archivo no es un Excel valido (.xlsx/.xls) o esta danado.");
        }
    }

    private static int findHeaderRow(Sheet sheet, DataFormatter formatter) {
        int maxRows = Math.min(sheet.getLastRowNum(), 10);
        for (int r = 0; r <= maxRows; r++) {
            Row row = sheet.getRow(r);
            if (row != null && findSkuColumn(row, formatter) >= 0) return r;
        }
        return 0;
    }

    private static int findSkuColumn(Row row, DataFormatter formatter) {
        if (row == null) return -1;
        short lastCellNum = row.getLastCellNum();
        for (int c = 0; c < lastCellNum; c++) {
            String header = normalizeHeader(formatter.formatCellValue(row.getCell(c)));
            if (header.equals("SKU") || header.equals("CODIGO") || header.equals("CODIGO SKU")) {
                return c;
            }
        }
        return -1;
    }

    private static String normalizeValue(String value) {
        if (value == null) return null;
        String s = value.trim().replaceAll("\\s+", " ");
        return s.isBlank() ? null : s;
    }

    private static String normalizeHeader(String value) {
        if (value == null) return "";
        String s = Normalizer.normalize(value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .replaceAll("[^A-Za-z0-9]+", " ")
                .trim()
                .replaceAll("\\s+", " ")
                .toUpperCase(Locale.ROOT);
        return s;
    }
}
