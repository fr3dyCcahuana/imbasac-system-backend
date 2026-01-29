package com.paulfernandosr.possystembackend.product.application.importer;

import com.paulfernandosr.possystembackend.product.domain.ProductCompetitiveImportError;
import com.paulfernandosr.possystembackend.product.domain.ProductCompetitiveImportResult;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedHashMap;
import java.util.Map;

public class CompetitivePriceCalculator {

    public static CompetitivePriceCalcOutput calculate(
            CompetitiveImportRow row,
            BigDecimal pctPublicA,
            BigDecimal pctPublicB,
            BigDecimal pctWholesaleC,
            BigDecimal pctWholesaleD,
            BigDecimal minPrice,
            ProductCompetitiveImportResult result
    ) {
        BigDecimal a = row.getPriceA();
        BigDecimal b = row.getPriceB();
        BigDecimal c = row.getPriceC();
        BigDecimal d = row.getPriceD();

        // ✅ Precios obligatorios
        if (a == null) { result.addError(simpleError(row, "Precio A", "REQUIRED", null, "Precio A es obligatorio.")); return fail(); }
        if (b == null) { result.addError(simpleError(row, "Precio B", "REQUIRED", null, "Precio B es obligatorio.")); return fail(); }
        if (c == null) { result.addError(simpleError(row, "Precio C", "REQUIRED", null, "Precio C es obligatorio.")); return fail(); }
        if (d == null) { result.addError(simpleError(row, "Precio D", "REQUIRED", null, "Precio D es obligatorio.")); return fail(); }

        // A/B vs CROSLAND PUBLICO
        if (row.getCompetPublic() != null && row.getCompetPublic().compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal compet = row.getCompetPublic();

            // Ajustar A solo si compet <= A
            if (compet.compareTo(a) <= 0) {
                BigDecimal targetA = percentTarget(compet, pctPublicA);
                if (targetA.compareTo(minPrice) < 0) {
                    result.addError(belowMinPct(row, "Precio A", a, "CROSLAND PUBLICO", compet, "pctPublicA", pctPublicA, targetA, minPrice));
                    return fail();
                }
                a = min(a, targetA);
            }

            // Ajustar B solo si compet <= B
            if (compet.compareTo(b) <= 0) {
                BigDecimal targetB = percentTarget(compet, pctPublicB);
                if (targetB.compareTo(minPrice) < 0) {
                    result.addError(belowMinPct(row, "Precio B", b, "CROSLAND PUBLICO", compet, "pctPublicB", pctPublicB, targetB, minPrice));
                    return fail();
                }
                b = min(b, targetB);
            }
        }

        // C/D vs CROSLAND MAYORISTA
        if (row.getCompetWholesale() != null && row.getCompetWholesale().compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal compet = row.getCompetWholesale();

            // Ajustar C solo si compet <= C
            if (compet.compareTo(c) <= 0) {
                BigDecimal targetC = percentTarget(compet, pctWholesaleC);
                if (targetC.compareTo(minPrice) < 0) {
                    result.addError(belowMinPct(row, "Precio C", c, "CROSLAND MAYORISTA", compet, "pctWholesaleC", pctWholesaleC, targetC, minPrice));
                    return fail();
                }
                c = min(c, targetC);
            }

            // Ajustar D solo si compet <= D
            if (compet.compareTo(d) <= 0) {
                BigDecimal targetD = percentTarget(compet, pctWholesaleD);
                if (targetD.compareTo(minPrice) < 0) {
                    result.addError(belowMinPct(row, "Precio D", d, "CROSLAND MAYORISTA", compet, "pctWholesaleD", pctWholesaleD, targetD, minPrice));
                    return fail();
                }
                d = min(d, targetD);
            }
        }

        return new CompetitivePriceCalcOutput(true, a, b, c, d);
    }

    private static CompetitivePriceCalcOutput fail() {
        return new CompetitivePriceCalcOutput(false, null, null, null, null);
    }

    private static BigDecimal percentTarget(BigDecimal competitor, BigDecimal pct) {
        // competitor * (1 - pct/100)
        BigDecimal rate = pct.divide(new BigDecimal("100"), 10, RoundingMode.HALF_UP);
        return competitor.multiply(BigDecimal.ONE.subtract(rate));
    }

    private static BigDecimal min(BigDecimal x, BigDecimal y) {
        return x.compareTo(y) <= 0 ? x : y;
    }

    private static ProductCompetitiveImportError simpleError(CompetitiveImportRow row, String field, String code, Object value, String msg) {
        return ProductCompetitiveImportError.builder()
                .row(row.getRowNumber())
                .sku(row.getSku())
                .field(field)
                .code(code)
                .value(value)
                .message(msg)
                .build();
    }

    private static ProductCompetitiveImportError belowMinPct(
            CompetitiveImportRow row,
            String field,
            BigDecimal currentPrice,
            String competitorField,
            BigDecimal competitorValue,
            String pctField,
            BigDecimal pctValue,
            BigDecimal target,
            BigDecimal minPrice
    ) {
        Map<String, Object> ctx = new LinkedHashMap<>();
        ctx.put("competitorField", competitorField);
        ctx.put("competitorValue", competitorValue);
        ctx.put(pctField, pctValue);
        ctx.put("target", target);
        ctx.put("minPrice", minPrice);

        return ProductCompetitiveImportError.builder()
                .row(row.getRowNumber())
                .sku(row.getSku())
                .field(field)
                .code("BELOW_MIN_PRICE")
                .value(currentPrice)
                .context(ctx)
                .message("El precio competitivo calculado cae por debajo del mínimo permitido.")
                .build();
    }
}
