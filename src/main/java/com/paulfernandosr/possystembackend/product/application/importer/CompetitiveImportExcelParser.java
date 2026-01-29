package com.paulfernandosr.possystembackend.product.application.importer;

import com.paulfernandosr.possystembackend.product.domain.ProductCompetitiveImportCommand;
import com.paulfernandosr.possystembackend.product.domain.ProductCompetitiveImportError;
import com.paulfernandosr.possystembackend.product.domain.ProductCompetitiveImportResult;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.util.*;

public class CompetitiveImportExcelParser {

    private static final String SHEET_PRODUCTS = "PRODUCTOS";
    private static final String SHEET_LISTS = "LISTAS";

    private static final List<String> EXPECTED_HEADERS = List.of(
            "Código (SKU)*",
            "Nombre / Descripción*",
            "Categoría",
            "Presentación",
            "Factor",
            "Tipo de origen",
            "País de origen",
            "Compatibilidad",
            "Ubicación en almacén",
            "CROSLAND PUBLICO",
            "Precio A",
            "Precio B",
            "CROSLAND MAYORISTA",
            "Precio C",
            "Precio D",
            "Costo referencial"
    );

    public static CompetitiveImportWorkbook parse(ProductCompetitiveImportCommand command, ProductCompetitiveImportResult result) {
        try (Workbook wb = new XSSFWorkbook(new ByteArrayInputStream(command.getFileBytes()))) {

            Sheet products = wb.getSheet(SHEET_PRODUCTS);
            Sheet lists = wb.getSheet(SHEET_LISTS);

            if (products == null || lists == null) {
                result.addError(ProductCompetitiveImportError.builder()
                        .row(0)
                        .field("Plantilla")
                        .code("INVALID_TEMPLATE")
                        .value(command.getOriginalFilename())
                        .message("La plantilla debe contener hojas 'PRODUCTOS' y 'LISTAS'.")
                        .build());
                return null;
            }

            validateHeader(products, result);
            if (result.hasErrors()) return null;

            return CompetitiveImportWorkbook.builder()
                    .allowedCategories(readListColumn(lists, 0))
                    .allowedPresentations(readListColumn(lists, 1))
                    .allowedOriginTypes(readListColumn(lists, 2))
                    .allowedCountries(readListColumn(lists, 3))
                    .rows(readProductRows(products))
                    .build();

        } catch (Exception e) {
            result.addError(ProductCompetitiveImportError.builder()
                    .row(0)
                    .field("Archivo")
                    .code("INVALID_FILE")
                    .message("El archivo no es un Excel válido (.xlsx) o está dañado.")
                    .build());
            return null;
        }
    }

    private static void validateHeader(Sheet products, ProductCompetitiveImportResult result) {
        Row header = products.getRow(0);
        if (header == null) {
            result.addError(ProductCompetitiveImportError.builder()
                    .row(1).field("Cabecera").code("INVALID_TEMPLATE")
                    .message("No se encontró la fila de cabecera en 'PRODUCTOS'.")
                    .build());
            return;
        }

        for (int i = 0; i < EXPECTED_HEADERS.size(); i++) {
            String expected = EXPECTED_HEADERS.get(i);
            String got = getString(header.getCell(i));
            String gotTrim = got == null ? "" : got.trim();

            // ✅ compatibilidad opcional: acepta "CROLANDO PUBLICO" si viene un excel viejo
            if ("CROSLAND PUBLICO".equalsIgnoreCase(expected)) {
                if ("CROSLAND PUBLICO".equalsIgnoreCase(gotTrim) || "CROLANDO PUBLICO".equalsIgnoreCase(gotTrim)) {
                    continue;
                }
            }

            if (!expected.equalsIgnoreCase(gotTrim)) {
                result.addError(ProductCompetitiveImportError.builder()
                        .row(1).field("Cabecera").code("INVALID_TEMPLATE")
                        .value(gotTrim)
                        .message("Cabecera inválida. Se esperaba '" + expected + "' en la columna " + (i + 1) + ".")
                        .build());
            }
        }
    }

    private static Set<String> readListColumn(Sheet lists, int colIndex) {
        Set<String> out = new HashSet<>();
        int last = lists.getLastRowNum();
        for (int r = 1; r <= last; r++) { // desde fila 2 (1-based) => índice 1
            Row row = lists.getRow(r);
            if (row == null) continue;
            String val = normalizeUpper(getString(row.getCell(colIndex)));
            if (val != null && !val.isBlank()) out.add(val);
        }
        return out;
    }

    private static List<CompetitiveImportRow> readProductRows(Sheet products) {
        List<CompetitiveImportRow> rows = new ArrayList<>();
        int last = products.getLastRowNum();

        for (int r = 1; r <= last; r++) {
            Row row = products.getRow(r);
            if (row == null) continue;

            boolean allEmpty = true;
            for (int c = 0; c < EXPECTED_HEADERS.size(); c++) {
                Cell cell = row.getCell(c);
                if (cell != null && cell.getCellType() == CellType.NUMERIC) { allEmpty = false; break; }
                String s = getString(cell);
                if (s != null && !s.trim().isBlank()) { allEmpty = false; break; }
            }
            if (allEmpty) continue;

            CompetitiveImportRow item = CompetitiveImportRow.builder()
                    .rowNumber(r + 1)
                    .sku(trimOrNull(getString(row.getCell(0))))
                    .name(trimOrNull(getString(row.getCell(1))))

                    .category(normalizeUpper(getString(row.getCell(2))))
                    .presentation(normalizeUpper(getString(row.getCell(3))))
                    .factor(getDecimal(row.getCell(4)))

                    .originType(normalizeUpper(getString(row.getCell(5))))
                    .originCountry(normalizeUpper(getString(row.getCell(6))))

                    .compatibility(trimOrNull(getString(row.getCell(7))))
                    .warehouseLocation(trimOrNull(getString(row.getCell(8))))

                    // ✅ columna corregida
                    .competPublic(getDecimal(row.getCell(9)))

                    .priceA(getDecimal(row.getCell(10)))
                    .priceB(getDecimal(row.getCell(11)))

                    .competWholesale(getDecimal(row.getCell(12)))
                    .priceC(getDecimal(row.getCell(13)))
                    .priceD(getDecimal(row.getCell(14)))

                    .costReference(getDecimal(row.getCell(15)))
                    .build();

            rows.add(item);
        }

        return rows;
    }

    private static String normalizeUpper(String s) {
        if (s == null) return null;
        String t = s.trim().replaceAll("\\s+", " ");
        if (t.isBlank()) return null;
        return t.toUpperCase(Locale.ROOT);
    }

    private static String trimOrNull(String s) {
        if (s == null) return null;
        String t = s.trim();
        return t.isBlank() ? null : t;
    }

    private static String getString(Cell cell) {
        if (cell == null) return null;
        return switch (cell.getCellType()) {
            case STRING -> cell.getStringCellValue();
            case NUMERIC -> {
                double v = cell.getNumericCellValue();
                if (Math.floor(v) == v) yield String.valueOf((long) v);
                yield String.valueOf(v);
            }
            case BOOLEAN -> String.valueOf(cell.getBooleanCellValue());
            case FORMULA -> {
                try { yield cell.getStringCellValue(); }
                catch (Exception e) {
                    try {
                        double v = cell.getNumericCellValue();
                        if (Math.floor(v) == v) yield String.valueOf((long) v);
                        yield String.valueOf(v);
                    } catch (Exception ex) {
                        yield null;
                    }
                }
            }
            default -> null;
        };
    }

    private static BigDecimal getDecimal(Cell cell) {
        if (cell == null) return null;
        try {
            if (cell.getCellType() == CellType.NUMERIC) {
                return BigDecimal.valueOf(cell.getNumericCellValue());
            }
            if (cell.getCellType() == CellType.STRING) {
                String t = cell.getStringCellValue();
                if (t == null) return null;
                t = t.trim();
                if (t.isBlank()) return null;
                t = t.replace(",", ".");
                return new BigDecimal(t);
            }
            if (cell.getCellType() == CellType.FORMULA) {
                try { return BigDecimal.valueOf(cell.getNumericCellValue()); }
                catch (Exception e) {
                    String t = cell.getStringCellValue();
                    if (t == null || t.trim().isBlank()) return null;
                    t = t.trim().replace(",", ".");
                    return new BigDecimal(t);
                }
            }
        } catch (Exception ignored) {
            return null;
        }
        return null;
    }
}
