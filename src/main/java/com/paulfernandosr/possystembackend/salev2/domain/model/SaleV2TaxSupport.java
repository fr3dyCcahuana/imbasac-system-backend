package com.paulfernandosr.possystembackend.salev2.domain.model;

import com.paulfernandosr.possystembackend.salev2.domain.exception.InvalidSaleV2Exception;

import java.math.BigDecimal;
import java.util.Locale;
import java.util.regex.Pattern;

public final class SaleV2TaxSupport {

    private static final Pattern DNI_PATTERN = Pattern.compile("^\\d{8}$");
    private static final Pattern RUC_PATTERN = Pattern.compile("^\\d{11}$");

    private SaleV2TaxSupport() {
    }

    public static TaxStatus normalizeTaxStatus(TaxStatus taxStatus) {
        return taxStatus != null ? taxStatus : TaxStatus.NO_GRAVADA;
    }

    public static boolean normalizeIgvIncluded(TaxStatus taxStatus, Boolean igvIncluded) {
        return normalizeTaxStatus(taxStatus) == TaxStatus.GRAVADA && Boolean.TRUE.equals(igvIncluded);
    }

    public static String normalizeTaxReason(TaxStatus taxStatus, String taxReason) {
        if (normalizeTaxStatus(taxStatus) == TaxStatus.GRAVADA) {
            return null;
        }
        return resolveSunatTaxCategory(taxStatus, taxReason).name();
    }

    public static SunatTaxCategory resolveSunatTaxCategory(TaxStatus taxStatus, String taxReason) {
        TaxStatus normalizedTaxStatus = normalizeTaxStatus(taxStatus);
        if (normalizedTaxStatus == TaxStatus.GRAVADA) {
            return SunatTaxCategory.GRAVADA;
        }

        String normalizedReason = normalizeText(taxReason);
        if (normalizedReason.isBlank()) {
            return SunatTaxCategory.EXONERADA;
        }

        if (normalizedReason.contains("INAFECTA")
                || normalizedReason.contains("NO AFECTA")
                || normalizedReason.contains("NO_GRAVADA_INAFECTA")
                || normalizedReason.equals("30")) {
            return SunatTaxCategory.INAFECTA;
        }

        if (normalizedReason.contains("EXONERADA")
                || normalizedReason.contains("EXONERADO")
                || normalizedReason.contains("NO_GRAVADA_EXONERADA")
                || normalizedReason.equals("20")) {
            return SunatTaxCategory.EXONERADA;
        }

        throw new InvalidSaleV2Exception("taxReason no soportado para taxStatus=NO_GRAVADA. Usa EXONERADA o INAFECTA.");
    }

    public static void validateCustomerDocumentForSunat(DocType docType,
                                                        String customerDocType,
                                                        String customerDocNumber,
                                                        BigDecimal total) {
        if (docType != DocType.BOLETA && docType != DocType.FACTURA) {
            return;
        }

        String normalizedDocType = normalizeText(customerDocType);
        String normalizedDocNumber = normalizeDigits(customerDocNumber);

        if (normalizedDocType.isBlank() || normalizedDocNumber.isBlank()) {
            throw new InvalidSaleV2Exception(docType + " requiere customerDocType y customerDocNumber para emisión SUNAT.");
        }

        boolean genericCustomer = isGenericCustomerDocumentType(normalizedDocType);

        if (docType == DocType.FACTURA) {
            if (!"RUC".equals(normalizedDocType)) {
                throw new InvalidSaleV2Exception("FACTURA requiere customerDocType=RUC.");
            }
            if (!RUC_PATTERN.matcher(normalizedDocNumber).matches()) {
                throw new InvalidSaleV2Exception("FACTURA requiere customerDocNumber con 11 dígitos (RUC).");
            }
            return;
        }

        if (genericCustomer) {
            if (total != null && total.compareTo(new BigDecimal("700.00")) > 0) {
                throw new InvalidSaleV2Exception("Ventas mayores a S/ 700.00 requieren cliente identificado. No se permite GEN/0.");
            }
            return;
        }

        if ("DNI".equals(normalizedDocType)) {
            if (!DNI_PATTERN.matcher(normalizedDocNumber).matches()) {
                throw new InvalidSaleV2Exception("BOLETA con DNI requiere customerDocNumber con 8 dígitos.");
            }
            return;
        }

        if ("RUC".equals(normalizedDocType)) {
            if (!RUC_PATTERN.matcher(normalizedDocNumber).matches()) {
                throw new InvalidSaleV2Exception("BOLETA con RUC requiere customerDocNumber con 11 dígitos.");
            }
            return;
        }

        throw new InvalidSaleV2Exception("Para BOLETA/FACTURA solo se soporta customerDocType=DNI, RUC o GEN/0 en este flujo SUNAT.");
    }

    public static boolean isGenericCustomerDocumentType(String value) {
        String v = normalizeText(value);
        return switch (v) {
            case "GEN", "GENERICO", "GENÉRICO", "GENERAL", "0",
                 "OTROS", "SIN_DOCUMENTO", "SIN DOCUMENTO" -> true;
            default -> false;
        };
    }

    public static String normalizeDigits(String value) {
        if (value == null) {
            return "";
        }
        return value.replaceAll("\\D", "");
    }

    public static String normalizeText(String value) {
        if (value == null) {
            return "";
        }
        return value.trim().toUpperCase(Locale.ROOT);
    }
}
