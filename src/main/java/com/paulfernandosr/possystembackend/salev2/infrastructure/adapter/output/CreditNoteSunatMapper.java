package com.paulfernandosr.possystembackend.salev2.infrastructure.adapter.output;

import com.paulfernandosr.possystembackend.sale.infrastructure.adapter.output.sunat.CurrencyType;
import com.paulfernandosr.possystembackend.sale.infrastructure.adapter.output.sunat.DocumentRequest;
import com.paulfernandosr.possystembackend.sale.infrastructure.adapter.output.sunat.IgvType;
import com.paulfernandosr.possystembackend.sale.infrastructure.adapter.output.sunat.PaymentMethod;
import com.paulfernandosr.possystembackend.sale.infrastructure.adapter.output.sunat.SunatProps;
import com.paulfernandosr.possystembackend.sale.infrastructure.adapter.output.sunat.UnitOfMeasureType;
import com.paulfernandosr.possystembackend.salev2.domain.exception.InvalidSaleV2Exception;
import com.paulfernandosr.possystembackend.salev2.domain.port.output.SaleCreditNoteRepository;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

public final class CreditNoteSunatMapper {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm:ss");

    private CreditNoteSunatMapper() {
    }

    public static DocumentRequest map(SunatProps props,
                                      SaleCreditNoteRepository.LockedSaleForCreditNote sale,
                                      CreditNoteForSunat note,
                                      List<CreditNoteLineForSunat> lines) {
        SunatProps.Business business = props.getBusiness();
        LocalDate issueDate = note.getIssueDate() != null ? note.getIssueDate() : LocalDate.now();
        LocalDateTime issueDateTime = issueDate.atTime(LocalDateTime.now().toLocalTime());

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
                        .serial(required(note.getSeries(), "creditNote.series"))
                        .number(String.valueOf(note.getNumber()))
                        .issueDate(issueDateTime.toLocalDate().format(DATE_FORMATTER))
                        .issueTime(issueDateTime.toLocalTime().format(TIME_FORMATTER))
                        .dueDate("")
                        .currencyId(mapCurrencyCode(sale.getCurrency()))
                        .paymentMethodId(PaymentMethod.CASH.getCode())
                        .totalTaxed(totalTaxed(sale, note).toPlainString())
                        .totalIgv(totalIgv(sale, note).toPlainString())
                        .totalExempted(totalExempted(sale, note).toPlainString())
                        .totalUnaffected(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP).toPlainString())
                        .globalDiscount(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP).toPlainString())
                        .documentTypeCode("07")
                        .note(blankIfNull(note.getReason()))
                        .relatedSeries(required(sale.getSeries(), "relatedSeries"))
                        .relatedNumber(String.valueOf(sale.getNumber()))
                        .relatedDocumentTypeCode(mapRelatedDocumentCode(sale.getDocType()))
                        .relatedReasonCode(required(note.getTypeCode(), "creditNote.typeCode"))
                        .relatedReasonDescription(required(note.getTypeDescription(), "creditNote.typeDescription"))
                        .build())
                .items(lines.stream().map(line -> mapItem(sale, line)).toList())
                .build();
    }

    private static DocumentRequest.Item mapItem(SaleCreditNoteRepository.LockedSaleForCreditNote sale,
                                                CreditNoteLineForSunat line) {
        BigDecimal qty = nz(line.getQuantity());
        if (qty.signum() <= 0) {
            throw new InvalidSaleV2Exception("Cantidad invalida en nota de credito para saleItemId=" + line.getSaleItemId());
        }

        BigDecimal basePrice = nz(line.getRevenueTotal()).divide(qty, 6, RoundingMode.HALF_UP);

        return DocumentRequest.Item.builder()
                .product(required(line.getDescription(), "description"))
                .quantity(qty.stripTrailingZeros().toPlainString())
                .basePrice(basePrice.toPlainString())
                .sunatCode(blankIfNull(line.getSunatCode()).isBlank() ? "01010101" : line.getSunatCode())
                .productCode(blankIfNull(line.getSku()))
                .unitCode(UnitOfMeasureType.PRODUCT_UNIT.getCode())
                .igvTypeCode(resolveIgvTypeCode(sale))
                .build();
    }

    private static BigDecimal totalTaxed(SaleCreditNoteRepository.LockedSaleForCreditNote sale, CreditNoteForSunat note) {
        return isNoGravada(sale)
                ? BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP)
                : nz(note.getSubtotal()).setScale(2, RoundingMode.HALF_UP);
    }

    private static BigDecimal totalIgv(SaleCreditNoteRepository.LockedSaleForCreditNote sale, CreditNoteForSunat note) {
        return isNoGravada(sale)
                ? BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP)
                : nz(note.getIgvAmount()).setScale(2, RoundingMode.HALF_UP);
    }

    private static BigDecimal totalExempted(SaleCreditNoteRepository.LockedSaleForCreditNote sale, CreditNoteForSunat note) {
        return isNoGravada(sale)
                ? nz(note.getSubtotal()).setScale(2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
    }

    private static String resolveIgvTypeCode(SaleCreditNoteRepository.LockedSaleForCreditNote sale) {
        return isNoGravada(sale) ? "20" : IgvType.TAXABLE_ONEROUS.getCode();
    }

    private static boolean isNoGravada(SaleCreditNoteRepository.LockedSaleForCreditNote sale) {
        return "NO_GRAVADA".equalsIgnoreCase(blankIfNull(sale.getTaxStatus()));
    }

    private static String mapRelatedDocumentCode(String docType) {
        String v = required(docType, "docType").trim().toUpperCase(Locale.ROOT);
        return switch (v) {
            case "FACTURA" -> "01";
            case "BOLETA" -> "03";
            default -> throw new InvalidSaleV2Exception("La nota de credito solo puede referenciar BOLETA/FACTURA. docType=" + docType);
        };
    }

    private static String mapIdentityDocumentCode(String saleDocType, String customerDocType) {
        String saleDoc = required(saleDocType, "docType").trim().toUpperCase(Locale.ROOT);
        String customerDoc = required(customerDocType, "customerDocType").trim().toUpperCase(Locale.ROOT);

        if (isGenericCustomerDocumentType(customerDoc)) {
            if ("FACTURA".equals(saleDoc)) {
                throw new InvalidSaleV2Exception("FACTURA no permite cliente generico. Debe usar RUC.");
            }
            return "0";
        }

        return switch (customerDoc) {
            case "DNI" -> "1";
            case "CE", "CARNET DE EXTRANJERIA" -> "4";
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
        String v = blankIfNull(value).trim().toUpperCase(Locale.ROOT);
        return switch (v) {
            case "GEN", "GENERICO", "GENERAL", "0", "OTROS", "SIN_DOCUMENTO", "SIN DOCUMENTO" -> true;
            default -> false;
        };
    }

    private static String mapCurrencyCode(String currency) {
        String v = required(currency, "currency").trim().toUpperCase(Locale.ROOT);
        return switch (v) {
            case "PEN" -> CurrencyType.SOL.getCode();
            case "USD" -> CurrencyType.DOLLAR.getCode();
            default -> throw new InvalidSaleV2Exception("Moneda no soportada para SUNAT: " + currency);
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
            throw new InvalidSaleV2Exception("Campo obligatorio para nota de credito SUNAT: " + field);
        }
        return value;
    }

    @Getter
    @Builder
    public static class CreditNoteForSunat {
        private String series;
        private Long number;
        private LocalDate issueDate;
        private String typeCode;
        private String typeDescription;
        private String reason;
        private BigDecimal subtotal;
        private BigDecimal igvAmount;
    }

    @Getter
    @Builder
    public static class CreditNoteLineForSunat {
        private Long saleItemId;
        private String sku;
        private String description;
        private BigDecimal quantity;
        private BigDecimal revenueTotal;
        private String sunatCode;
    }
}
