package com.paulfernandosr.possystembackend.salev2.infrastructure.adapter.output;

import com.paulfernandosr.possystembackend.sale.infrastructure.adapter.output.sunat.CurrencyType;
import com.paulfernandosr.possystembackend.sale.infrastructure.adapter.output.sunat.DocumentRequest;
import com.paulfernandosr.possystembackend.sale.infrastructure.adapter.output.sunat.IgvType;
import com.paulfernandosr.possystembackend.sale.infrastructure.adapter.output.sunat.PaymentMethod;
import com.paulfernandosr.possystembackend.sale.infrastructure.adapter.output.sunat.SunatProps;
import com.paulfernandosr.possystembackend.sale.infrastructure.adapter.output.sunat.UnitOfMeasureType;
import com.paulfernandosr.possystembackend.salev2.domain.exception.InvalidSaleV2Exception;
import com.paulfernandosr.possystembackend.salev2.domain.port.output.SaleV2SunatRepository;
import com.paulfernandosr.possystembackend.salev2.infrastructure.adapter.output.sunat.SunatCodeInferer;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

public final class SaleV2SunatMapper {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm:ss");

    private SaleV2SunatMapper() {
    }

    public static DocumentRequest map(SunatProps props,
                                      SaleV2SunatRepository.LockedSunatSale sale,
                                      List<SaleV2SunatRepository.SaleItemForSunat> items) {

        SunatProps.Business business = props.getBusiness();
        LocalDateTime baseDateTime = sale.getCreatedAt() != null ? sale.getCreatedAt() : LocalDateTime.now();
        LocalDateTime emissionDateTime = sale.getIssueDate() != null
                ? sale.getIssueDate().atTime(baseDateTime.toLocalTime())
                : baseDateTime;

        return DocumentRequest.builder()
                .business(DocumentRequest.Business.builder()
                        .ruc(business.getRuc())
                        .businessName(business.getBusinessName())
                        .tradeName(business.getTradeName())
                        .taxAddress(business.getTaxAddress())
                        .ubigeo(business.getUbigeo())
                        .neighborhood(business.getNeighborhood())
                        .district(business.getDistrict())
                        .province(business.getProvince())
                        .department(business.getDepartment())
                        .mode(props.getMode())
                        .username(props.getUsername())
                        .password(props.getPassword())
                        .build())
                .customer(DocumentRequest.Customer.builder()
                        .fullName(required(sale.getCustomerName(), "customerName"))
                        .documentNumber(mapCustomerDocumentNumber(sale.getCustomerDocType(), sale.getCustomerDocNumber()))
                        .entityTypeCode(mapIdentityDocumentCode(sale.getDocType(), sale.getCustomerDocType()))
                        .address(blankIfNull(sale.getCustomerAddress()))
                        .build())
                .sale(DocumentRequest.Sale.builder()
                        .serial(required(sale.getSeries(), "series"))
                        .number(String.valueOf(sale.getNumber()))
                        .issueDate(emissionDateTime.toLocalDate().format(DATE_FORMATTER))
                        .issueTime(emissionDateTime.toLocalTime().format(TIME_FORMATTER))
                        .dueDate("")
                        .currencyId(mapCurrencyCode(sale.getCurrency()))
                        .paymentMethodId(PaymentMethod.CASH.getCode())
                        .totalTaxed(totalTaxed(sale).toPlainString())
                        .totalIgv(totalIgv(sale).toPlainString())
                        .totalExempted(totalExempted(sale).toPlainString())
                        .totalUnaffected(totalUnaffected(sale).toPlainString())
                        .globalDiscount(nz(sale.getDiscountTotal()).setScale(2, RoundingMode.HALF_UP).toPlainString())
                        .documentTypeCode(mapDocumentCode(sale.getDocType()))
                        .note(blankIfNull(sale.getNotes()))
                        .build())
                .items(items.stream().map(i -> mapItem(i, sale)).toList())
                .build();
    }

    private static DocumentRequest.Item mapItem(SaleV2SunatRepository.SaleItemForSunat item,
                                                SaleV2SunatRepository.LockedSunatSale sale) {
        BigDecimal qty = nz(item.getQuantity());
        if (qty.signum() <= 0) {
            throw new InvalidSaleV2Exception("Cantidad inválida para emisión SUNAT en línea " + item.getLineNumber());
        }

        BigDecimal basePrice = nz(item.getRevenueTotal())
                .divide(qty, 6, RoundingMode.HALF_UP);

        String description = required(item.getDescription(), "description");
        String productCategory = required(item.getProductCategory(), "productCategory");

        String inferredSunatCode;
        try {
            inferredSunatCode = SunatCodeInferer.infer(description, productCategory);
        } catch (Exception ex) {
            throw new InvalidSaleV2Exception(
                    "No se pudo inferir código SUNAT para la línea " + item.getLineNumber()
                            + " producto=" + description
                            + " categoría=" + productCategory
                            + ". Detalle: " + ex.getMessage()
            );
        }

        return DocumentRequest.Item.builder()
                .product(description)
                .quantity(qty.stripTrailingZeros().toPlainString())
                .basePrice(basePrice.toPlainString())
                .sunatCode(inferredSunatCode)
                .productCode(blankIfNull(item.getSku()))
                .unitCode(UnitOfMeasureType.PRODUCT_UNIT.getCode())
                .igvTypeCode(resolveIgvTypeCode(sale))
                .build();
    }

    private static String mapDocumentCode(String docType) {
        String v = required(docType, "docType").trim().toUpperCase();
        return switch (v) {
            case "FACTURA" -> "01";
            case "BOLETA" -> "03";
            default -> throw new InvalidSaleV2Exception("Solo se puede emitir a SUNAT documentos BOLETA/FACTURA. docType=" + docType);
        };
    }

    private static String mapIdentityDocumentCode(String saleDocType, String customerDocType) {
        String saleDoc = required(saleDocType, "docType").trim().toUpperCase();
        String customerDoc = required(customerDocType, "customerDocType").trim().toUpperCase();

        if (isGenericCustomerDocumentType(customerDoc)) {
            if ("FACTURA".equals(saleDoc)) {
                throw new InvalidSaleV2Exception("FACTURA no permite customerDocType=GEN/0. Debe usar RUC.");
            }
            return "0";
        }

        return switch (customerDoc) {
            case "DNI" -> "1";
            case "CE", "CARNET DE EXTRANJERIA", "CARNET DE EXTRANJERÍA" -> "4";
            case "RUC" -> "6";
            case "PASSPORT", "PASAPORTE" -> "7";
            default -> throw new InvalidSaleV2Exception("Tipo de documento de cliente no soportado para SUNAT: " + customerDocType);
        };
    }

    private static String mapCustomerDocumentNumber(String customerDocType, String customerDocNumber) {
        if (isGenericCustomerDocumentType(customerDocType)) {
            return "0";
        }
        return required(customerDocNumber, "customerDocNumber").trim();
    }

    private static boolean isGenericCustomerDocumentType(String value) {
        String v = blankIfNull(value).trim().toUpperCase();
        return switch (v) {
            case "GEN", "GENERICO", "GENÉRICO", "GENERAL", "0",
                 "OTROS", "SIN_DOCUMENTO", "SIN DOCUMENTO" -> true;
            default -> false;
        };
    }

    private static String mapCurrencyCode(String currency) {
        String v = required(currency, "currency").trim().toUpperCase();
        return switch (v) {
            case "PEN" -> CurrencyType.SOL.getCode();
            case "USD" -> CurrencyType.DOLLAR.getCode();
            default -> throw new InvalidSaleV2Exception("Moneda no soportada para SUNAT: " + currency);
        };
    }

    private static BigDecimal totalTaxed(SaleV2SunatRepository.LockedSunatSale sale) {
        return isNoGravada(sale) ? BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP)
                : nz(sale.getSubtotal()).setScale(2, RoundingMode.HALF_UP);
    }

    private static BigDecimal totalIgv(SaleV2SunatRepository.LockedSunatSale sale) {
        return isNoGravada(sale) ? BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP)
                : nz(sale.getIgvAmount()).setScale(2, RoundingMode.HALF_UP);
    }

    private static BigDecimal totalExempted(SaleV2SunatRepository.LockedSunatSale sale) {
        return isNoGravada(sale)
                ? nz(sale.getSubtotal()).setScale(2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
    }

    private static BigDecimal totalUnaffected(SaleV2SunatRepository.LockedSunatSale sale) {
        return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
    }

    private static String resolveIgvTypeCode(SaleV2SunatRepository.LockedSunatSale sale) {
        return isNoGravada(sale) ? "20" : IgvType.TAXABLE_ONEROUS.getCode();
    }

    private static boolean isNoGravada(SaleV2SunatRepository.LockedSunatSale sale) {
        return "NO_GRAVADA".equalsIgnoreCase(blankIfNull(sale.getTaxStatus()));
    }

    private static BigDecimal nz(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private static String blankIfNull(String value) {
        return value == null ? "" : value;
    }

    private static String required(String value, String field) {
        if (value == null || value.trim().isEmpty()) {
            throw new InvalidSaleV2Exception("Campo obligatorio para emisión SUNAT: " + field);
        }
        return value;
    }
}
