package com.paulfernandosr.possystembackend.countersale.application;

import com.paulfernandosr.possystembackend.countersale.domain.exception.InvalidCounterSaleException;
import com.paulfernandosr.possystembackend.countersale.domain.port.input.GetCounterSaleUseCase;
import com.paulfernandosr.possystembackend.countersale.infrastructure.adapter.input.dto.*;
import lombok.*;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.*;

@Component
@RequiredArgsConstructor
class CounterSaleSunatCombinationComposer {

    static final BigDecimal TOTAL_LIMIT = new BigDecimal("700.0000");
    static final String CUSTOMER_MODE_VENTA_DIARIA = "VENTA_DIARIA";

    private final GetCounterSaleUseCase getCounterSaleUseCase;

    ComposedResult compose(Long anchorCounterSaleId, CounterSaleSunatCombinationRequest request) {
        if (anchorCounterSaleId == null) {
            throw new InvalidCounterSaleException("counterSaleId ancla es obligatorio.");
        }
        CounterSaleSunatCombinationRequest safeRequest = request == null
                ? CounterSaleSunatCombinationRequest.builder().build()
                : request;

        String customerMode = normalizeCustomerMode(safeRequest.getCustomerMode());
        if (!CUSTOMER_MODE_VENTA_DIARIA.equals(customerMode)) {
            throw new InvalidCounterSaleException("Solo se soporta customerMode=VENTA_DIARIA en este flujo.");
        }

        List<Long> ids = new ArrayList<>();
        ids.add(anchorCounterSaleId);
        if (safeRequest.getAdditionalCounterSaleIds() != null) {
            for (Long id : safeRequest.getAdditionalCounterSaleIds()) {
                if (id == null || Objects.equals(id, anchorCounterSaleId) || ids.contains(id)) {
                    continue;
                }
                ids.add(id);
            }
        }

        Map<Long, BigDecimal> overrideMap = new HashMap<>();
        if (safeRequest.getLineOverrides() != null) {
            for (CounterSaleSunatCombinationRequest.LineOverride override : safeRequest.getLineOverrides()) {
                if (override == null || override.getCounterSaleItemId() == null) continue;
                overrideMap.put(override.getCounterSaleItemId(), nz(override.getEmittedUnitPrice()).setScale(4, RoundingMode.HALF_UP));
            }
        }

        List<String> validationMessages = new ArrayList<>();
        List<SelectedCounterSale> selectedCounterSales = new ArrayList<>();
        List<SelectedLine> selectedLines = new ArrayList<>();

        CounterSaleDetailResponse anchor = getCounterSaleUseCase.getById(anchorCounterSaleId);
        ensureEligible(anchor, validationMessages);

        for (Long id : ids) {
            CounterSaleDetailResponse detail = Objects.equals(id, anchorCounterSaleId) ? anchor : getCounterSaleUseCase.getById(id);
            ensureEligible(detail, validationMessages);
            ensureCompatible(anchor, detail, validationMessages);

            selectedCounterSales.add(SelectedCounterSale.builder()
                    .counterSaleId(detail.getCounterSaleId())
                    .detail(detail)
                    .build());

            if (detail.getItems() != null) {
                for (CounterSaleItemResponse item : detail.getItems()) {
                    ensureItemEligible(detail, item, validationMessages);
                    BigDecimal emittedUnitPrice = overrideMap.getOrDefault(item.getCounterSaleItemId(), nz(item.getUnitPrice()).setScale(4, RoundingMode.HALF_UP));
                    if (emittedUnitPrice.compareTo(BigDecimal.ZERO) < 0) {
                        validationMessages.add("El precio emitido no puede ser negativo. counterSaleItemId=" + item.getCounterSaleItemId());
                    }

                    LineAmounts amounts = computeLineAmounts(emittedUnitPrice, nz(item.getQuantity()), isGravada(anchor), Boolean.TRUE.equals(anchor.getIgvIncluded()), nz(anchor.getIgvRate(), new BigDecimal("18.00")));
                    selectedLines.add(SelectedLine.builder()
                            .counterSaleId(detail.getCounterSaleId())
                            .counterSale(detail)
                            .item(item)
                            .emittedUnitPrice(emittedUnitPrice)
                            .emittedRevenueTotal(amounts.base)
                            .emittedGrossTotal(amounts.gross)
                            .build());
                }
            }
        }

        Totals totals = computeTotals(selectedLines, isGravada(anchor), Boolean.TRUE.equals(anchor.getIgvIncluded()), nz(anchor.getIgvRate(), new BigDecimal("18.00")));
        boolean withinLimit = totals.total.compareTo(TOTAL_LIMIT) <= 0;
        if (!withinLimit) {
            validationMessages.add("El monto total del comprobante no puede superar 700 soles.");
        }

        boolean canEmit = validationMessages.isEmpty();

        return ComposedResult.builder()
                .anchor(anchor)
                .customerMode(customerMode)
                .series(normalizeSeries(safeRequest.getSeries()))
                .issueDate(safeRequest.getIssueDate() != null ? safeRequest.getIssueDate() : LocalDate.now())
                .paymentMethod(normalizePaymentMethod(safeRequest.getPaymentMethod()))
                .notes(trimToNull(safeRequest.getNotes()))
                .selectedCounterSales(selectedCounterSales)
                .selectedLines(selectedLines)
                .totals(totals)
                .withinLimit(withinLimit)
                .canEmit(canEmit)
                .validationMessages(validationMessages)
                .build();
    }

    CounterSaleSunatCombinationValidationResponse toValidationResponse(ComposedResult result) {
        return CounterSaleSunatCombinationValidationResponse.builder()
                .anchorCounterSaleId(result.getAnchor().getCounterSaleId())
                .customerMode(result.getCustomerMode())
                .docType("BOLETA")
                .series(result.getSeries())
                .issueDate(result.getIssueDate())
                .customer(CounterSaleSunatCombinationCustomerResponse.builder()
                        .name("VENTA DIARIA")
                        .docTypeCode("0")
                        .docNumber("0")
                        .address("-")
                        .build())
                .currency(result.getAnchor().getCurrency())
                .taxStatus(result.getAnchor().getTaxStatus())
                .igvRate(result.getAnchor().getIgvRate())
                .igvIncluded(result.getAnchor().getIgvIncluded())
                .composedSubtotal(result.getTotals().subtotal)
                .composedDiscountTotal(BigDecimal.ZERO.setScale(4, RoundingMode.HALF_UP))
                .composedIgvAmount(result.getTotals().igv)
                .composedTotal(result.getTotals().total)
                .totalLimit(TOTAL_LIMIT)
                .remainingLimit(TOTAL_LIMIT.subtract(result.getTotals().total).setScale(4, RoundingMode.HALF_UP))
                .withinLimit(result.getWithinLimit())
                .canEmit(result.getCanEmit())
                .validationMessages(result.getValidationMessages())
                .counterSales(result.getSelectedCounterSales().stream().map(this::toCounterSaleResponse).toList())
                .lines(result.getSelectedLines().stream().map(this::toLineResponse).toList())
                .build();
    }

    private CounterSaleSunatCombinationCounterSaleResponse toCounterSaleResponse(SelectedCounterSale row) {
        CounterSaleDetailResponse d = row.getDetail();
        return CounterSaleSunatCombinationCounterSaleResponse.builder()
                .counterSaleId(d.getCounterSaleId())
                .sourceDocumentLabel("VENTANILLA " + d.getSeries() + "-" + d.getNumber())
                .series(d.getSeries())
                .number(d.getNumber())
                .status(d.getStatus())
                .subtotal(nz(d.getSubtotal()).setScale(4, RoundingMode.HALF_UP))
                .discountTotal(nz(d.getDiscountTotal()).setScale(4, RoundingMode.HALF_UP))
                .igvAmount(nz(d.getIgvAmount()).setScale(4, RoundingMode.HALF_UP))
                .total(nz(d.getTotal()).setScale(4, RoundingMode.HALF_UP))
                .associatedToSunat(Boolean.TRUE.equals(d.getAssociatedToSunat()))
                .associatedSaleId(d.getAssociatedSaleId())
                .associatedDocType(d.getAssociatedDocType())
                .associatedSeries(d.getAssociatedSeries())
                .associatedNumber(d.getAssociatedNumber())
                .associatedAt(d.getAssociatedAt())
                .build();
    }

    private CounterSaleSunatCombinationLineResponse toLineResponse(SelectedLine line) {
        CounterSaleItemResponse item = line.getItem();
        return CounterSaleSunatCombinationLineResponse.builder()
                .counterSaleId(line.getCounterSaleId())
                .counterSaleItemId(item.getCounterSaleItemId())
                .sourceDocumentLabel("VENTANILLA " + line.getCounterSale().getSeries() + "-" + line.getCounterSale().getNumber())
                .sourceLineNumber(item.getLineNumber())
                .productId(item.getProductId())
                .sku(item.getSku())
                .description(item.getDescription())
                .quantity(nz(item.getQuantity()).setScale(3, RoundingMode.HALF_UP))
                .originalUnitPrice(nz(item.getUnitPrice()).setScale(4, RoundingMode.HALF_UP))
                .emittedUnitPrice(line.getEmittedUnitPrice())
                .discountPercent(nz(item.getDiscountPercent()).setScale(4, RoundingMode.HALF_UP))
                .discountAmount(nz(item.getDiscountAmount()).setScale(4, RoundingMode.HALF_UP))
                .originalRevenueTotal(nz(item.getRevenueTotal()).setScale(4, RoundingMode.HALF_UP))
                .emittedRevenueTotal(line.getEmittedRevenueTotal())
                .build();
    }

    private void ensureEligible(CounterSaleDetailResponse detail, List<String> errors) {
        if (!"EMITIDA".equalsIgnoreCase(blank(detail.getStatus()))) {
            errors.add("El counter-sale debe estar EMITIDA. counterSaleId=" + detail.getCounterSaleId());
        }
        if (Boolean.TRUE.equals(detail.getAssociatedToSunat())) {
            errors.add("El counter-sale ya está asociado a SUNAT. counterSaleId=" + detail.getCounterSaleId());
        }
    }

    private void ensureCompatible(CounterSaleDetailResponse anchor, CounterSaleDetailResponse current, List<String> errors) {
        if (!Objects.equals(anchor.getStationId(), current.getStationId())) {
            errors.add("Las operaciones de ventanilla deben pertenecer a la misma estación. counterSaleId=" + current.getCounterSaleId());
        }
        if (!Objects.equals(blank(anchor.getCurrency()), blank(current.getCurrency()))) {
            errors.add("Las operaciones de ventanilla deben compartir la misma moneda. counterSaleId=" + current.getCounterSaleId());
        }
        if (!Objects.equals(blank(anchor.getTaxStatus()), blank(current.getTaxStatus()))) {
            errors.add("Las operaciones de ventanilla deben compartir el mismo taxStatus. counterSaleId=" + current.getCounterSaleId());
        }
        if (nz(anchor.getIgvRate()).compareTo(nz(current.getIgvRate())) != 0) {
            errors.add("Las operaciones de ventanilla deben compartir el mismo igvRate. counterSaleId=" + current.getCounterSaleId());
        }
        if (!Objects.equals(Boolean.TRUE.equals(anchor.getIgvIncluded()), Boolean.TRUE.equals(current.getIgvIncluded()))) {
            errors.add("Las operaciones de ventanilla deben compartir el mismo igvIncluded. counterSaleId=" + current.getCounterSaleId());
        }
    }

    private void ensureItemEligible(CounterSaleDetailResponse detail, CounterSaleItemResponse item, List<String> errors) {
        if (!"VENDIDO".equalsIgnoreCase(blank(item.getLineKind()))) {
            errors.add("Solo se permiten líneas VENDIDO en venta diaria. counterSaleItemId=" + item.getCounterSaleItemId());
        }
        if (nz(item.getDiscountPercent()).compareTo(BigDecimal.ZERO) != 0 || nz(item.getDiscountAmount()).compareTo(BigDecimal.ZERO) != 0) {
            errors.add("No se permiten descuentos por línea en venta diaria. counterSaleItemId=" + item.getCounterSaleItemId());
        }
        if (item.getSerialUnits() != null && !item.getSerialUnits().isEmpty()) {
            errors.add("No se puede emitir venta diaria con productos serializados. counterSaleId=" + detail.getCounterSaleId() + ", counterSaleItemId=" + item.getCounterSaleItemId());
        }
    }

    private Totals computeTotals(List<SelectedLine> lines, boolean gravada, boolean igvIncluded, BigDecimal igvRate) {
        BigDecimal subtotal = BigDecimal.ZERO;
        BigDecimal total = BigDecimal.ZERO;
        for (SelectedLine line : lines) {
            subtotal = subtotal.add(line.getEmittedRevenueTotal());
            total = total.add(gravada && igvIncluded ? line.getEmittedGrossTotal() : line.getEmittedRevenueTotal());
        }
        subtotal = subtotal.setScale(4, RoundingMode.HALF_UP);
        BigDecimal igv = BigDecimal.ZERO.setScale(4, RoundingMode.HALF_UP);
        if (gravada) {
            if (igvIncluded) {
                igv = total.subtract(subtotal).setScale(4, RoundingMode.HALF_UP);
            } else {
                igv = subtotal.multiply(nz(igvRate)).divide(new BigDecimal("100"), 4, RoundingMode.HALF_UP);
                total = subtotal.add(igv).setScale(4, RoundingMode.HALF_UP);
            }
        } else {
            total = subtotal;
        }
        return Totals.builder().subtotal(subtotal).igv(igv).total(total).build();
    }

    private LineAmounts computeLineAmounts(BigDecimal unitPrice, BigDecimal quantity, boolean gravada, boolean igvIncluded, BigDecimal igvRate) {
        BigDecimal gross = nz(unitPrice).multiply(nz(quantity)).setScale(4, RoundingMode.HALF_UP);
        if (gravada && igvIncluded) {
            BigDecimal divisor = BigDecimal.ONE.add(nz(igvRate).divide(new BigDecimal("100"), 10, RoundingMode.HALF_UP));
            BigDecimal base = gross.divide(divisor, 4, RoundingMode.HALF_UP);
            return new LineAmounts(base, gross);
        }
        return new LineAmounts(gross, gross);
    }

    private boolean isGravada(CounterSaleDetailResponse detail) {
        return "GRAVADA".equalsIgnoreCase(blank(detail.getTaxStatus()));
    }

    private String normalizeSeries(String series) {
        String normalized = trimToNull(series);
        return normalized == null ? "B003" : normalized.toUpperCase();
    }

    private String normalizePaymentMethod(String paymentMethod) {
        String normalized = trimToNull(paymentMethod);
        return normalized == null ? "EFECTIVO" : normalized.toUpperCase();
    }

    private String normalizeCustomerMode(String customerMode) {
        String normalized = trimToNull(customerMode);
        return normalized == null ? CUSTOMER_MODE_VENTA_DIARIA : normalized.toUpperCase();
    }

    private BigDecimal nz(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private BigDecimal nz(BigDecimal value, BigDecimal fallback) {
        return value == null ? fallback : value;
    }

    private String trimToNull(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String blank(String value) {
        return value == null ? "" : value;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    static class ComposedResult {
        private CounterSaleDetailResponse anchor;
        private String customerMode;
        private String series;
        private LocalDate issueDate;
        private String paymentMethod;
        private String notes;
        private List<SelectedCounterSale> selectedCounterSales;
        private List<SelectedLine> selectedLines;
        private Totals totals;
        private Boolean withinLimit;
        private Boolean canEmit;
        private List<String> validationMessages;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    static class SelectedCounterSale {
        private Long counterSaleId;
        private CounterSaleDetailResponse detail;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    static class SelectedLine {
        private Long counterSaleId;
        private CounterSaleDetailResponse counterSale;
        private CounterSaleItemResponse item;
        private BigDecimal emittedUnitPrice;
        private BigDecimal emittedRevenueTotal;
        private BigDecimal emittedGrossTotal;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    static class LineAmounts {
        private BigDecimal base;
        private BigDecimal gross;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    static class Totals {
        private BigDecimal subtotal;
        private BigDecimal igv;
        private BigDecimal total;
    }
}
