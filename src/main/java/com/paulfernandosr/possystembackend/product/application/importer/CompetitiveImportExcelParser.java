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

    private static final List<String> EXPECTED_HEADERS_V1 = List.of(
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

    private static final List<String> EXPECTED_HEADERS_V2 = List.of(
            "Código (SKU)*",
            "Nombre / Descripción*",
            "Categoría",
            "Marca",
            "Modelo",
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

    private static final List<String> EXPECTED_HEADERS_V3 = List.of(
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
            "Costo referencial",
            "Marca"
    );

    private enum TemplateVersion { V1, V2, V3 }

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

            TemplateVersion version = validateHeader(products, result);
            if (result.hasErrors() || version == null) return null;

            Set<String> allowedCategories = readListColumnUpper(lists, 0);
            Set<String> allowedPresentations = readListColumnUpper(lists, 1);
            Set<String> allowedOriginTypes = readListColumnUpper(lists, 2);
            Set<String> allowedCountries = readListColumnUpper(lists, 3);

            Set<String> allowedBrands = Set.of();
            Set<String> allowedModels = Set.of();
            if (version == TemplateVersion.V2 || version == TemplateVersion.V3) {
                allowedBrands = readListColumnTrim(lists, 4);
            }
            if (version == TemplateVersion.V2) {
                allowedModels = readListColumnTrim(lists, 5);
            }

            return CompetitiveImportWorkbook.builder()
                    .allowedCategories(allowedCategories)
                    .allowedPresentations(allowedPresentations)
                    .allowedOriginTypes(allowedOriginTypes)
                    .allowedCountries(allowedCountries)
                    .allowedBrands(allowedBrands)
                    .allowedModels(allowedModels)
                    .rows(readProductRows(products, version))
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

    private static TemplateVersion validateHeader(Sheet products, ProductCompetitiveImportResult result) {
        Row header = products.getRow(0);
        if (header == null) {
            result.addError(ProductCompetitiveImportError.builder()
                    .row(1).field("Cabecera").code("INVALID_TEMPLATE")
                    .message("No se encontró la fila de cabecera en 'PRODUCTOS'.")
                    .build());
            return null;
        }

        int mismV3 = countHeaderMismatches(header, EXPECTED_HEADERS_V3);
        if (mismV3 == 0) return TemplateVersion.V3;

        int mismV2 = countHeaderMismatches(header, EXPECTED_HEADERS_V2);
        if (mismV2 == 0) return TemplateVersion.V2;

        int mismV1 = countHeaderMismatches(header, EXPECTED_HEADERS_V1);
        if (mismV1 == 0) return TemplateVersion.V1;

        List<String> expected = closestExpectedHeaders(mismV1, mismV2, mismV3);
        for (int i = 0; i < expected.size(); i++) {
            String exp = expected.get(i);
            String got = getString(header.getCell(i));
            String gotTrim = got == null ? "" : got.trim();

            if (headerCellMatches(exp, gotTrim)) continue;

            result.addError(ProductCompetitiveImportError.builder()
                    .row(1)
                    .field("Cabecera")
                    .code("INVALID_TEMPLATE")
                    .value(gotTrim)
                    .message("Cabecera inválida. Se esperaba '" + exp + "' en la columna " + (i + 1) + ".")
                    .build());
        }

        return null;
    }

    private static List<String> closestExpectedHeaders(int mismV1, int mismV2, int mismV3) {
        if (mismV3 <= mismV2 && mismV3 <= mismV1) return EXPECTED_HEADERS_V3;
        if (mismV2 <= mismV1) return EXPECTED_HEADERS_V2;
        return EXPECTED_HEADERS_V1;
    }

    private static int countHeaderMismatches(Row header, List<String> expected) {
        int mismatches = 0;
        for (int i = 0; i < expected.size(); i++) {
            String exp = expected.get(i);
            String got = getString(header.getCell(i));
            String gotTrim = got == null ? "" : got.trim();
            if (!headerCellMatches(exp, gotTrim)) mismatches++;
        }
        return mismatches;
    }

    private static boolean headerCellMatches(String expected, String gotTrim) {
        if (expected == null) return gotTrim == null || gotTrim.isBlank();

        if ("CROSLAND PUBLICO".equalsIgnoreCase(expected)) {
            return "CROSLAND PUBLICO".equalsIgnoreCase(gotTrim) || "CROLANDO PUBLICO".equalsIgnoreCase(gotTrim);
        }
        if ("CROSLAND MAYORISTA".equalsIgnoreCase(expected)) {
            return "CROSLAND MAYORISTA".equalsIgnoreCase(gotTrim) || "CROLANDO MAYORISTA".equalsIgnoreCase(gotTrim);
        }

        return expected.equalsIgnoreCase(gotTrim);
    }

    private static Set<String> readListColumnUpper(Sheet lists, int colIndex) {
        Set<String> out = new HashSet<>();
        int last = lists.getLastRowNum();
        for (int r = 1; r <= last; r++) {
            Row row = lists.getRow(r);
            if (row == null) continue;
            String val = normalizeUpper(getString(row.getCell(colIndex)));
            if (val != null && !val.isBlank()) out.add(val);
        }
        return out;
    }

    private static Set<String> readListColumnTrim(Sheet lists, int colIndex) {
        Set<String> out = new HashSet<>();
        int last = lists.getLastRowNum();
        for (int r = 1; r <= last; r++) {
            Row row = lists.getRow(r);
            if (row == null) continue;
            String val = normalizeTrim(getString(row.getCell(colIndex)));
            if (val != null && !val.isBlank()) out.add(val);
        }
        return out;
    }

    private static List<CompetitiveImportRow> readProductRows(Sheet products, TemplateVersion version) {
        List<CompetitiveImportRow> rows = new ArrayList<>();
        int last = products.getLastRowNum();

        int colCount = switch (version) {
            case V3 -> EXPECTED_HEADERS_V3.size();
            case V2 -> EXPECTED_HEADERS_V2.size();
            case V1 -> EXPECTED_HEADERS_V1.size();
        };

        for (int r = 1; r <= last; r++) {
            Row row = products.getRow(r);
            if (row == null) continue;

            boolean allEmpty = true;
            for (int c = 0; c < colCount; c++) {
                Cell cell = row.getCell(c);
                if (cell != null && cell.getCellType() == CellType.NUMERIC) { allEmpty = false; break; }
                String s = getString(cell);
                if (s != null && !s.trim().isBlank()) { allEmpty = false; break; }
            }
            if (allEmpty) continue;

            CompetitiveImportRow.CompetitiveImportRowBuilder b = CompetitiveImportRow.builder()
                    .rowNumber(r + 1)
                    .sku(trimOrNull(getString(row.getCell(0))))
                    .name(trimOrNull(getString(row.getCell(1))))
                    .category(normalizeUpper(getString(row.getCell(2))));

            if (version == TemplateVersion.V2) {
                b.brand(normalizeTrim(getString(row.getCell(3))))
                 .model(normalizeTrim(getString(row.getCell(4))))
                 .presentation(normalizeUpper(getString(row.getCell(5))))
                 .factor(getDecimal(row.getCell(6)))
                 .originType(normalizeUpper(getString(row.getCell(7))))
                 .originCountry(normalizeUpper(getString(row.getCell(8))))
                 .compatibility(trimOrNull(getString(row.getCell(9))))
                 .warehouseLocation(trimOrNull(getString(row.getCell(10))))
                 .competPublic(getDecimal(row.getCell(11)))
                 .priceA(getDecimal(row.getCell(12)))
                 .priceB(getDecimal(row.getCell(13)))
                 .competWholesale(getDecimal(row.getCell(14)))
                 .priceC(getDecimal(row.getCell(15)))
                 .priceD(getDecimal(row.getCell(16)))
                 .costReference(getDecimal(row.getCell(17)));
            } else {
                b.presentation(normalizeUpper(getString(row.getCell(3))))
                 .factor(getDecimal(row.getCell(4)))
                 .originType(normalizeUpper(getString(row.getCell(5))))
                 .originCountry(normalizeUpper(getString(row.getCell(6))))
                 .compatibility(trimOrNull(getString(row.getCell(7))))
                 .warehouseLocation(trimOrNull(getString(row.getCell(8))))
                 .competPublic(getDecimal(row.getCell(9)))
                 .priceA(getDecimal(row.getCell(10)))
                 .priceB(getDecimal(row.getCell(11)))
                 .competWholesale(getDecimal(row.getCell(12)))
                 .priceC(getDecimal(row.getCell(13)))
                 .priceD(getDecimal(row.getCell(14)))
                 .costReference(getDecimal(row.getCell(15)));

                if (version == TemplateVersion.V3) {
                    b.brand(normalizeTrim(getString(row.getCell(16))));
                }
            }

            rows.add(b.build());
        }

        return rows;
    }

    private static String normalizeUpper(String s) {
        if (s == null) return null;
        String t = s.trim().replaceAll("\\s+", " ");
        if (t.isBlank()) return null;
        return t.toUpperCase(Locale.ROOT);
    }

    private static String normalizeTrim(String s) {
        if (s == null) return null;
        String t = s.trim().replaceAll("\\s+", " ");
        return t.isBlank() ? null : t;
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
