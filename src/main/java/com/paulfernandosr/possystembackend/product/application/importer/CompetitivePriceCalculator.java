package com.paulfernandosr.possystembackend.product.application.importer;

import com.paulfernandosr.possystembackend.product.domain.ProductCompetitiveImportError;
import com.paulfernandosr.possystembackend.product.domain.ProductCompetitiveImportResult;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

public class CompetitivePriceCalculator {

    public static CompetitivePriceCalcOutput calculate(
            CompetitiveImportRow row,
            BigDecimal montoRestaPublico,
            BigDecimal montoRestaMayorista,
            BigDecimal minPrice,
            ProductCompetitiveImportResult result
    ) {
        BigDecimal a = row.getPriceA();
        BigDecimal b = row.getPriceB();
        BigDecimal c = row.getPriceC();
        BigDecimal d = row.getPriceD();

        if (a == null) {
            result.addError(simpleError(row, "Precio A", "REQUIRED", null, "Precio A es obligatorio."));
            return new CompetitivePriceCalcOutput(false, null, null, null, null);
        }
        if (b == null) {
            result.addError(simpleError(row, "Precio B", "REQUIRED", null, "Precio B es obligatorio."));
            return new CompetitivePriceCalcOutput(false, null, null, null, null);
        }
        if (c == null) {
            result.addError(simpleError(row, "Precio C", "REQUIRED", null, "Precio C es obligatorio."));
            return new CompetitivePriceCalcOutput(false, null, null, null, null);
        }
        if (d == null) {
            result.addError(simpleError(row, "Precio D", "REQUIRED", null, "Precio D es obligatorio."));
            return new CompetitivePriceCalcOutput(false, null, null, null, null);
        }

        // A/B contra CROLANDO PUBLICO
        if (row.getCompetPublic() != null && row.getCompetPublic().compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal compet = row.getCompetPublic();
            BigDecimal targetPublic = compet.subtract(montoRestaPublico);

            // A
            if (a == null) {
                // no vino A -> se define desde target
                if (targetPublic.compareTo(minPrice) < 0) {
                    result.addError(buildBelowMinError(
                            row.getRowNumber(), row.getSku(),
                            "CROLANDO PUBLICO", "BELOW_MIN_PRICE", compet,
                            "El targetPublic calculado cae por debajo del mínimo permitido.",
                            "CROLANDO PUBLICO", compet, montoRestaPublico, targetPublic, minPrice
                    ));
                    return new CompetitivePriceCalcOutput(false, null, null, null, null);
                }
                a = targetPublic;
            } else {
                // solo ajustar si compet <= nuestro precio
                if (compet.compareTo(a) <= 0) {
                    if (targetPublic.compareTo(minPrice) < 0) {
                        result.addError(buildBelowMinError(
                                row.getRowNumber(), row.getSku(),
                                "CROLANDO PUBLICO", "BELOW_MIN_PRICE", compet,
                                "El targetPublic calculado cae por debajo del mínimo permitido.",
                                "CROLANDO PUBLICO", compet, montoRestaPublico, targetPublic, minPrice
                        ));
                        return new CompetitivePriceCalcOutput(false, null, null, null, null);
                    }
                    a = min(a, targetPublic);
                }
                // else: compet > a -> no ajustar
            }

            // B
            if (b == null) {
                if (targetPublic.compareTo(minPrice) < 0) {
                    result.addError(buildBelowMinError(
                            row.getRowNumber(), row.getSku(),
                            "CROLANDO PUBLICO", "BELOW_MIN_PRICE", compet,
                            "El targetPublic calculado cae por debajo del mínimo permitido.",
                            "CROLANDO PUBLICO", compet, montoRestaPublico, targetPublic, minPrice
                    ));
                    return new CompetitivePriceCalcOutput(false, null, null, null, null);
                }
                b = targetPublic;
            } else {
                if (compet.compareTo(b) <= 0) {
                    if (targetPublic.compareTo(minPrice) < 0) {
                        result.addError(buildBelowMinError(
                                row.getRowNumber(), row.getSku(),
                                "CROLANDO PUBLICO", "BELOW_MIN_PRICE", compet,
                                "El targetPublic calculado cae por debajo del mínimo permitido.",
                                "CROLANDO PUBLICO", compet, montoRestaPublico, targetPublic, minPrice
                        ));
                        return new CompetitivePriceCalcOutput(false, null, null, null, null);
                    }
                    b = min(b, targetPublic);
                }
            }
        }


        // C/D contra CROSLAND MAYORISTA
        if (row.getCompetWholesale() != null && row.getCompetWholesale().compareTo(BigDecimal.ZERO) > 0) {

            BigDecimal compet = row.getCompetWholesale();
            BigDecimal targetWholesale = compet.subtract(montoRestaMayorista);

            // Ajuste solo si la competencia es <= a nuestro precio C
            if (compet.compareTo(c) <= 0) {
                if (targetWholesale.compareTo(minPrice) < 0) {
                    result.addError(buildBelowMinError(
                            row.getRowNumber(),
                            row.getSku(),
                            "CROSLAND MAYORISTA",
                            "BELOW_MIN_PRICE",
                            compet,
                            "El targetWholesale calculado cae por debajo del mínimo permitido.",
                            "CROSLAND MAYORISTA",
                            compet,
                            montoRestaMayorista,
                            targetWholesale,
                            minPrice
                    ));
                    return new CompetitivePriceCalcOutput(false, null, null, null, null);
                }
                c = min(c, targetWholesale);
            }
            // else: compet > c -> no ajustar C

            // Ajuste solo si la competencia es <= a nuestro precio D
            if (compet.compareTo(d) <= 0) {
                if (targetWholesale.compareTo(minPrice) < 0) {
                    result.addError(buildBelowMinError(
                            row.getRowNumber(),
                            row.getSku(),
                            "CROSLAND MAYORISTA",
                            "BELOW_MIN_PRICE",
                            compet,
                            "El targetWholesale calculado cae por debajo del mínimo permitido.",
                            "CROSLAND MAYORISTA",
                            compet,
                            montoRestaMayorista,
                            targetWholesale,
                            minPrice
                    ));
                    return new CompetitivePriceCalcOutput(false, null, null, null, null);
                }
                d = min(d, targetWholesale);
            }
            // else: compet > d -> no ajustar D
        }

        // Requeridos: A/B/C/D deben existir al final
        if (a == null) {
            result.addError(simpleError(row, "Precio A", "REQUIRED", null, "Precio A requerido (o debe existir CROLANDO PUBLICO)."));
            return new CompetitivePriceCalcOutput(false, null, null, null, null);
        }
        if (b == null) {
            result.addError(simpleError(row, "Precio B", "REQUIRED", null, "Precio B requerido (o debe existir CROLANDO PUBLICO)."));
            return new CompetitivePriceCalcOutput(false, null, null, null, null);
        }
        if (c == null) {
            result.addError(simpleError(row, "Precio C", "REQUIRED", null, "Precio C requerido (o debe existir CROSLAND MAYORISTA)."));
            return new CompetitivePriceCalcOutput(false, null, null, null, null);
        }
        if (d == null) {
            result.addError(simpleError(row, "Precio D", "REQUIRED", null, "Precio D requerido (o debe existir CROSLAND MAYORISTA)."));
            return new CompetitivePriceCalcOutput(false, null, null, null, null);
        }

        return new CompetitivePriceCalcOutput(true, a, b, c, d);
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

    private static ProductCompetitiveImportError buildBelowMinError(
            int row,
            String sku,
            String field,
            String code,
            Object value,
            String message,
            String competitorField,
            BigDecimal competitorValue,
            BigDecimal montoResta,
            BigDecimal target,
            BigDecimal minPrice
    ) {
        Map<String, Object> context = new LinkedHashMap<>();
        context.put("competitorField", competitorField);
        context.put("competitorValue", competitorValue);
        if ("CROLANDO PUBLICO".equals(competitorField)) context.put("montoRestaPublico", montoResta);
        else context.put("montoRestaMayorista", montoResta);
        context.put("target", target);
        context.put("minPrice", minPrice);

        return ProductCompetitiveImportError.builder()
                .row(row)
                .sku(sku)
                .field(field)
                .code(code)
                .value(value)
                .context(context)
                .message(message)
                .build();
    }
}
