package com.paulfernandosr.possystembackend.product.application.importer;

import com.paulfernandosr.possystembackend.product.domain.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

public class CompetitiveImportBuilder {

    public static CompetitiveImportBuildOutput validateAndBuild(
            CompetitiveImportWorkbook workbook,
            BigDecimal montoRestaPublico,
            BigDecimal montoRestaMayorista,
            BigDecimal minPrice,
            ProductCompetitiveImportResult result
    ) {
        List<Product> products = new ArrayList<>();
        List<ProductCompetitiveImportPreviewRow> preview = new ArrayList<>();
        Set<String> categoriesUsed = new HashSet<>();

        // Duplicados en el Excel
        Map<String, Integer> skuFirstRow = new HashMap<>();

        int rowsRead = 0;
        int validRows = 0;

        for (CompetitiveImportRow row : workbook.getRows()) {
            rowsRead++;

            String sku = row.getSku();
            String name = row.getName();

            // SKU requerido
            if (sku == null || sku.isBlank()) {
                addError(result, row, null, "Código (SKU)*", "REQUIRED", sku, "SKU requerido.");
                continue;
            }
            if (sku.length() > 50) {
                addError(result, row, sku, "Código (SKU)*", "TOO_LONG", sku, "SKU debe ser <= 50 caracteres.");
                continue;
            }

            // Nombre requerido
            if (name == null || name.trim().isBlank()) {
                addError(result, row, sku, "Nombre / Descripción*", "REQUIRED", name, "Nombre requerido.");
                continue;
            }
            if (name.length() > 200) {
                addError(result, row, sku, "Nombre / Descripción*", "TOO_LONG", name, "Nombre debe ser <= 200 caracteres.");
                continue;
            }

            // Duplicado en archivo
            if (skuFirstRow.containsKey(sku)) {
                addError(result, row, sku, "Código (SKU)*", "DUPLICATE_IN_FILE", sku,
                        "SKU duplicado en el archivo (primera vez en fila " + skuFirstRow.get(sku) + ").");
                continue;
            } else {
                skuFirstRow.put(sku, row.getRowNumber());
            }

            // Listas permitidas (si viene)
            if (row.getCategory() != null && !workbook.getAllowedCategories().contains(row.getCategory())) {
                addError(result, row, sku, "Categoría", "NOT_ALLOWED", row.getCategory(), "Categoría no permitida.");
                continue;
            }
            if (row.getPresentation() != null && !workbook.getAllowedPresentations().contains(row.getPresentation())) {
                addError(result, row, sku, "Presentación", "NOT_ALLOWED", row.getPresentation(), "Presentación no permitida.");
                continue;
            }
            if (row.getOriginType() != null && !workbook.getAllowedOriginTypes().contains(row.getOriginType())) {
                addError(result, row, sku, "Tipo de origen", "NOT_ALLOWED", row.getOriginType(), "Tipo de origen no permitido.");
                continue;
            }
            if (row.getOriginCountry() != null && !workbook.getAllowedCountries().contains(row.getOriginCountry())) {
                addError(result, row, sku, "País de origen", "NOT_ALLOWED", row.getOriginCountry(), "País de origen no permitido.");
                continue;
            }

            // numéricos básicos
            if (row.getFactor() != null && row.getFactor().compareTo(BigDecimal.ZERO) <= 0) {
                addError(result, row, sku, "Factor", "INVALID_NUMBER", row.getFactor(), "Factor debe ser > 0.");
                continue;
            }
            if (!geZero(row.getCompetPublic())) {
                addError(result, row, sku, "CROLANDO PUBLICO", "INVALID_NUMBER", row.getCompetPublic(), "Debe ser >= 0.");
                continue;
            }
            if (!geZero(row.getCompetWholesale())) {
                addError(result, row, sku, "CROSLAND MAYORISTA", "INVALID_NUMBER", row.getCompetWholesale(), "Debe ser >= 0.");
                continue;
            }
            if (!geZero(row.getPriceA()) || !geZero(row.getPriceB()) || !geZero(row.getPriceC()) || !geZero(row.getPriceD()) || !geZero(row.getCostReference())) {
                addError(result, row, sku, "Precios", "INVALID_NUMBER", null, "Precios y costo referencial deben ser >= 0.");
                continue;
            }

            // cálculo competitivo
            CompetitivePriceCalcOutput calc = CompetitivePriceCalculator.calculate(
                    row, montoRestaPublico, montoRestaMayorista, minPrice, result
            );
            if (!calc.ok()) {
                // CompetitivePriceCalculator ya agrega errores por fila/campo
                continue;
            }

            BigDecimal priceA = round2(calc.priceA());
            BigDecimal priceB = round2(calc.priceB());
            BigDecimal priceC = round2(calc.priceC());
            BigDecimal priceD = round2(calc.priceD());

            // orden obligatorio A>=B>=C>=D
            if (priceB.compareTo(priceA) > 0) priceB = priceA;
            if (priceC.compareTo(priceB) > 0) priceC = priceB;
            if (priceD.compareTo(priceC) > 0) priceD = priceC;

            // piso mínimo final por campo
            if (priceA.compareTo(minPrice) < 0) {
                addError(result, row, sku, "Precio A", "BELOW_MIN_PRICE", priceA, "Precio A cae por debajo del mínimo permitido.");
                continue;
            }
            if (priceB.compareTo(minPrice) < 0) {
                addError(result, row, sku, "Precio B", "BELOW_MIN_PRICE", priceB, "Precio B cae por debajo del mínimo permitido.");
                continue;
            }
            if (priceC.compareTo(minPrice) < 0) {
                addError(result, row, sku, "Precio C", "BELOW_MIN_PRICE", priceC, "Precio C cae por debajo del mínimo permitido.");
                continue;
            }
            if (priceD.compareTo(minPrice) < 0) {
                addError(result, row, sku, "Precio D", "BELOW_MIN_PRICE", priceD, "Precio D cae por debajo del mínimo permitido.");
                continue;
            }

            BigDecimal factor = row.getFactor() == null ? BigDecimal.ONE : row.getFactor();

            Product product = Product.builder()
                    .sku(sku)
                    .name(name)
                    .productType("BIEN")
                    .manageBySerial(false)
                    .factoryCode(sku)
                    .barcode(sku)
                    .facturableSunat(true)
                    .affectsStock(true)
                    .giftAllowed(false)

                    .category(row.getCategory())
                    .presentation(row.getPresentation())
                    .factor(factor)

                    .originType(row.getOriginType())
                    .originCountry(row.getOriginCountry())
                    .compatibility(row.getCompatibility())
                    .warehouseLocation(row.getWarehouseLocation())

                    .priceA(priceA)
                    .priceB(priceB)
                    .priceC(priceC)
                    .priceD(priceD)

                    // costReference con 4 decimales si viene
                    .costReference(row.getCostReference() == null ? null : row.getCostReference().setScale(4, RoundingMode.HALF_UP))
                    .build();

            products.add(product);
            validRows++;

            if (row.getCategory() != null) categoriesUsed.add(row.getCategory());

            preview.add(ProductCompetitiveImportPreviewRow.builder()
                    .row(row.getRowNumber())
                    .sku(sku)
                    .priceA(priceA)
                    .priceB(priceB)
                    .priceC(priceC)
                    .priceD(priceD)
                    .costReference(product.getCostReference())
                    .build());
        }

        result.getSummary().setRowsRead(rowsRead);
        result.getSummary().setValidRows(validRows);

        return new CompetitiveImportBuildOutput(products, preview, categoriesUsed);
    }

    private static boolean geZero(BigDecimal v) {
        return v == null || v.compareTo(BigDecimal.ZERO) >= 0;
    }

    private static BigDecimal round2(BigDecimal v) {
        return v.setScale(2, RoundingMode.HALF_UP);
    }

    private static void addError(ProductCompetitiveImportResult result, CompetitiveImportRow row, String sku,
                                 String field, String code, Object value, String message) {
        result.addError(ProductCompetitiveImportError.builder()
                .row(row.getRowNumber())
                .sku(sku)
                .field(field)
                .code(code)
                .value(value)
                .message(message)
                .build());
    }
}
