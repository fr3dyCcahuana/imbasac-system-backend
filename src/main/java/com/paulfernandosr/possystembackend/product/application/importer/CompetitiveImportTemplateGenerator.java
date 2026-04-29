package com.paulfernandosr.possystembackend.product.application.importer;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddressList;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.web.server.ResponseStatusException;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR;

public final class CompetitiveImportTemplateGenerator {

    private static final String SHEET_PRODUCTS = "PRODUCTOS";
    private static final String SHEET_LISTS = "LISTAS";
    private static final String SHEET_README = "README";
    private static final int FIRST_DATA_ROW = 1;
    private static final int LAST_DATA_ROW = 1000;

    private static final List<String> HEADERS = List.of(
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

    private static final List<String> DEFAULT_CATEGORIES = List.of(
            "MOTOR Y PARTES INTERNAS",
            "FRENOS",
            "SUSPENSION Y AMORTIGUADORES",
            "TRANSMISION Y ARRASTRE",
            "ELECTRICO Y ENCENDIDO",
            "CARROCERIA Y PLASTICOS",
            "LUBRICANTES Y ACEITES",
            "FILTROS",
            "NEUMATICOS Y AROS",
            "MANDOS Y CONTROLES",
            "ILUMINACION Y SEÑALIZACION",
            "ACCESORIOS Y PERSONALIZACION",
            "CONSUMIBLES Y FERRETERIA",
            "HERRAMIENTAS",
            "EQUIPOS Y SEGURIDAD",
            "AMORTIGUADORES",
            "MOTOR",
            "MOTOCICLETAS",
            "REPUESTOS"
    );

    private static final List<String> PRESENTATIONS = List.of("UNIDAD", "PAR", "SET", "KIT");
    private static final List<String> ORIGIN_TYPES = List.of("NACIONAL", "IMPORTADO", "FABRICA");
    private static final List<String> COUNTRIES = List.of("INDIA", "CHINA", "PERÚ", "BRASIL", "FRANCIA", "ALEMANIA", "COLOMBIA", "JAPÓN", "COREA DEL SUR");

    private CompetitiveImportTemplateGenerator() {
    }

    public static byte[] generate(List<String> categoriesFromDb, List<String> brandsFromDb) {
        try (Workbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet products = wb.createSheet(SHEET_PRODUCTS);
            Sheet lists = wb.createSheet(SHEET_LISTS);
            Sheet readme = wb.createSheet(SHEET_README);

            CellStyle headerStyle = buildHeaderStyle(wb);
            CellStyle readmeTitleStyle = buildReadmeTitleStyle(wb);
            CellStyle readmeBodyStyle = buildReadmeBodyStyle(wb);
            CellStyle moneyStyle = wb.createCellStyle();
            moneyStyle.setDataFormat(wb.getCreationHelper().createDataFormat().getFormat("#,##0.00"));
            CellStyle decimalStyle = wb.createCellStyle();
            decimalStyle.setDataFormat(wb.getCreationHelper().createDataFormat().getFormat("#,##0.####"));

            writeProductsSheet(products, headerStyle, moneyStyle, decimalStyle);
            writeListsSheet(lists, headerStyle, mergeDefaults(categoriesFromDb, DEFAULT_CATEGORIES), PRESENTATIONS, ORIGIN_TYPES, COUNTRIES, normalizeList(brandsFromDb));
            writeReadmeSheet(readme, readmeTitleStyle, readmeBodyStyle);
            applyValidations(products, mergeDefaults(categoriesFromDb, DEFAULT_CATEGORIES), normalizeList(brandsFromDb));

            wb.write(out);
            return out.toByteArray();
        } catch (Exception e) {
            throw new ResponseStatusException(INTERNAL_SERVER_ERROR, "No se pudo generar la plantilla de importación competitiva.");
        }
    }

    private static void writeProductsSheet(Sheet sheet, CellStyle headerStyle, CellStyle moneyStyle, CellStyle decimalStyle) {
        Row header = sheet.createRow(0);
        header.setHeightInPoints(24);
        for (int i = 0; i < HEADERS.size(); i++) {
            Cell cell = header.createCell(i);
            cell.setCellValue(HEADERS.get(i));
            cell.setCellStyle(headerStyle);
        }

        sheet.createFreezePane(0, 1);
        int[] widths = {18, 36, 28, 18, 12, 18, 18, 28, 24, 18, 14, 14, 20, 14, 14, 18, 24};
        for (int i = 0; i < widths.length; i++) sheet.setColumnWidth(i, widths[i] * 256);

        for (int r = FIRST_DATA_ROW; r <= LAST_DATA_ROW; r++) {
            Row row = sheet.createRow(r);
            row.createCell(4).setCellStyle(decimalStyle);
            row.createCell(9).setCellStyle(moneyStyle);
            row.createCell(10).setCellStyle(moneyStyle);
            row.createCell(11).setCellStyle(moneyStyle);
            row.createCell(12).setCellStyle(moneyStyle);
            row.createCell(13).setCellStyle(moneyStyle);
            row.createCell(14).setCellStyle(moneyStyle);
            row.createCell(15).setCellStyle(moneyStyle);
        }
    }

    private static void writeListsSheet(Sheet sheet, CellStyle headerStyle, List<String> categories, List<String> presentations, List<String> originTypes, List<String> countries, List<String> brands) {
        List<List<String>> columns = List.of(categories, presentations, originTypes, countries, brands);
        String[] headers = {"Categorías (edite aquí)", "Presentación (edite aquí)", "Tipo de origen (edite aquí)", "País de origen (edite aquí)", "Marcas (edite aquí)"};

        Row header = sheet.createRow(0);
        header.setHeightInPoints(24);
        for (int c = 0; c < headers.length; c++) {
            Cell cell = header.createCell(c);
            cell.setCellValue(headers[c]);
            cell.setCellStyle(headerStyle);
        }

        int max = columns.stream().mapToInt(List::size).max().orElse(0);
        for (int r = 0; r < max; r++) {
            Row row = sheet.createRow(r + 1);
            for (int c = 0; c < columns.size(); c++) {
                List<String> values = columns.get(c);
                if (r < values.size()) row.createCell(c).setCellValue(values.get(r));
            }
        }

        for (int i = 0; i < headers.length; i++) sheet.setColumnWidth(i, 30 * 256);
        sheet.createFreezePane(0, 1);
    }

    private static void writeReadmeSheet(Sheet sheet, CellStyle titleStyle, CellStyle bodyStyle) {
        String[] lines = {
                "Plantilla masiva de productos - importación competitiva",
                "",
                "La hoja PRODUCTOS viene vacía. Llene desde la fila 2 hacia abajo.",
                "",
                "Campos obligatorios:",
                " - Código (SKU)*",
                " - Nombre / Descripción*",
                " - Precio A, Precio B, Precio C y Precio D",
                "",
                "Listas desplegables:",
                " - Categoría, Presentación, Tipo de origen, País de origen y Marca.",
                " - Si la marca no existe en la lista, puede escribirla manualmente en la columna Marca.",
                " - Al importar, el backend registra primero la marca nueva y luego continúa con el producto.",
                "",
                "Columnas de competencia:",
                " - CROSLAND PUBLICO: referencia para Precio A y Precio B.",
                " - CROSLAND MAYORISTA: referencia para Precio C y Precio D.",
                "",
                "Campos automáticos en backend:",
                " - productType = BIEN",
                " - manageBySerial = FALSO",
                " - factoryCode = sku",
                " - barcode = sku",
                " - facturableSunat = VERDADERO",
                " - affectsStock = VERDADERO",
                " - giftAllowed = FALSO"
        };

        for (int r = 0; r < lines.length; r++) {
            Row row = sheet.createRow(r);
            Cell cell = row.createCell(0);
            cell.setCellValue(lines[r]);
            cell.setCellStyle(r == 0 ? titleStyle : bodyStyle);
        }
        sheet.setColumnWidth(0, 95 * 256);
    }

    private static void applyValidations(Sheet products, List<String> categories, List<String> brands) {
        applyListValidation(products, 2, "LISTAS!$A$2:$A$" + Math.max(2, categories.size() + 1), true);
        applyListValidation(products, 3, "LISTAS!$B$2:$B$" + (PRESENTATIONS.size() + 1), true);
        applyListValidation(products, 5, "LISTAS!$C$2:$C$" + (ORIGIN_TYPES.size() + 1), true);
        applyListValidation(products, 6, "LISTAS!$D$2:$D$" + (COUNTRIES.size() + 1), true);
        applyListValidation(products, 16, "LISTAS!$E$2:$E$" + Math.max(2, brands.size() + 1), false);
    }

    private static void applyListValidation(Sheet sheet, int column, String formula, boolean strict) {
        DataValidationHelper helper = sheet.getDataValidationHelper();
        DataValidationConstraint constraint = helper.createFormulaListConstraint(formula);
        CellRangeAddressList addressList = new CellRangeAddressList(FIRST_DATA_ROW, LAST_DATA_ROW, column, column);
        DataValidation validation = helper.createValidation(constraint, addressList);
        validation.setEmptyCellAllowed(true);
        validation.setSuppressDropDownArrow(true);
        if (validation instanceof org.apache.poi.xssf.usermodel.XSSFDataValidation xssfValidation) {
            xssfValidation.setSuppressDropDownArrow(true);
        }
        validation.setShowErrorBox(strict);
        sheet.addValidationData(validation);
    }

    private static CellStyle buildHeaderStyle(Workbook wb) {
        Font font = wb.createFont();
        font.setBold(true);
        font.setColor(IndexedColors.WHITE.getIndex());
        CellStyle style = wb.createCellStyle();
        style.setFont(font);
        style.setFillForegroundColor(IndexedColors.DARK_BLUE.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        style.setAlignment(HorizontalAlignment.CENTER);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        style.setWrapText(true);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        return style;
    }

    private static CellStyle buildReadmeTitleStyle(Workbook wb) {
        Font font = wb.createFont();
        font.setBold(true);
        font.setFontHeightInPoints((short) 14);
        CellStyle style = wb.createCellStyle();
        style.setFont(font);
        return style;
    }

    private static CellStyle buildReadmeBodyStyle(Workbook wb) {
        Font font = wb.createFont();
        font.setFontHeightInPoints((short) 11);
        CellStyle style = wb.createCellStyle();
        style.setFont(font);
        style.setWrapText(true);
        return style;
    }

    private static List<String> mergeDefaults(List<String> first, List<String> defaults) {
        Set<String> out = new LinkedHashSet<>();
        normalizeList(first).forEach(out::add);
        normalizeList(defaults).forEach(out::add);
        return new ArrayList<>(out);
    }

    private static List<String> normalizeList(List<String> values) {
        if (values == null || values.isEmpty()) return List.of();
        return values.stream()
                .filter(v -> v != null && !v.isBlank())
                .map(v -> v.trim().replaceAll("\\s+", " "))
                .distinct()
                .toList();
    }
}
