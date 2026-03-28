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
                        .documentNumber(required(sale.getCustomerDocNumber(), "customerDocNumber"))
                        .entityTypeCode(mapIdentityDocumentCode(required(sale.getCustomerDocType(), "customerDocType")))
                        .address(blankIfNull(sale.getCustomerAddress()))
                        .build())
                .sale(DocumentRequest.Sale.builder()
                        .serial(required(sale.getSeries(), "series"))
                        .number(String.valueOf(sale.getNumber()))
                        .issueDate(emissionDateTime.toLocalDate().format(DATE_FORMATTER))
                        .issueTime(emissionDateTime.toLocalTime().format(TIME_FORMATTER))
                        .dueDate("")
                        .currencyId(mapCurrencyCode(sale.getCurrency()))
                        .paymentMethodId(mapPaymentMethodCode(sale.getPaymentType()))
                        .totalTaxed(nz(sale.getSubtotal()).setScale(2, RoundingMode.HALF_UP).toPlainString())
                        .totalIgv(nz(sale.getIgvAmount()).setScale(2, RoundingMode.HALF_UP).toPlainString())
                        .totalExempted("")
                        .totalUnaffected("")
                        .globalDiscount(nz(sale.getDiscountTotal()).setScale(2, RoundingMode.HALF_UP).toPlainString())
                        .documentTypeCode(mapDocumentCode(sale.getDocType()))
                        .note(blankIfNull(sale.getNotes()))
                        .build())
                .items(items.stream().map(SaleV2SunatMapper::mapItem).toList())
                .build();
    }

    private static DocumentRequest.Item mapItem(SaleV2SunatRepository.SaleItemForSunat item) {
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
                .igvTypeCode(IgvType.TAXABLE_ONEROUS.getCode())
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

    private static String mapIdentityDocumentCode(String docType) {
        String v = docType.trim().toUpperCase();
        return switch (v) {
            case "DNI" -> "1";
            case "CE", "CARNET DE EXTRANJERIA" -> "4";
            case "RUC" -> "6";
            case "PASSPORT", "PASAPORTE" -> "7";
            default -> throw new InvalidSaleV2Exception("Tipo de documento de cliente no soportado para SUNAT: " + docType);
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

    private static String mapPaymentMethodCode(String paymentType) {
        String v = required(paymentType, "paymentType").trim().toUpperCase();
        return switch (v) {
            case "CONTADO" -> PaymentMethod.CASH.getCode();
            case "CREDITO" -> PaymentMethod.CREDIT.getCode();
            default -> throw new InvalidSaleV2Exception("Tipo de pago no soportado para SUNAT: " + paymentType);
        };
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
