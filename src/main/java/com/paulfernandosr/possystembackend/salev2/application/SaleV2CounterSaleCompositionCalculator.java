package com.paulfernandosr.possystembackend.salev2.application;

import com.paulfernandosr.possystembackend.countersale.infrastructure.adapter.input.dto.CounterSaleDetailResponse;
import com.paulfernandosr.possystembackend.countersale.infrastructure.adapter.input.dto.CounterSaleItemResponse;
import com.paulfernandosr.possystembackend.salev2.domain.exception.InvalidSaleV2Exception;
import com.paulfernandosr.possystembackend.salev2.domain.model.LineKind;
import com.paulfernandosr.possystembackend.salev2.domain.model.PaymentMethod;
import com.paulfernandosr.possystembackend.salev2.domain.model.ProductSnapshot;
import com.paulfernandosr.possystembackend.salev2.domain.model.TaxStatus;
import com.paulfernandosr.possystembackend.salev2.domain.port.output.ProductSnapshotRepository;
import com.paulfernandosr.possystembackend.salev2.infrastructure.adapter.input.dto.SaleV2AdminEditRequest;
import com.paulfernandosr.possystembackend.salev2.infrastructure.adapter.input.dto.SaleV2ComposeSunatRequest;
import com.paulfernandosr.possystembackend.salev2.infrastructure.adapter.input.dto.SaleV2DetailResponse;
import com.paulfernandosr.possystembackend.salev2.infrastructure.adapter.input.dto.SaleV2ItemResponse;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
@RequiredArgsConstructor
public class SaleV2CounterSaleCompositionCalculator {

    private static final BigDecimal ONE_HUNDRED = new BigDecimal("100");
    private static final BigDecimal DEFAULT_IGV_RATE = new BigDecimal("18.00");

    private final ProductSnapshotRepository productSnapshotRepository;

    public CompositionPlan buildPlan(SaleV2DetailResponse sale,
                                     List<CounterSaleDetailResponse> counterSales,
                                     SaleV2ComposeSunatRequest request,
                                     boolean requireEditReason) {
        if (sale == null) {
            throw new InvalidSaleV2Exception("Venta no encontrada.");
        }
        if (request == null) {
            throw new InvalidSaleV2Exception("Request vacío.");
        }

        validateEditReason(request.getEditReason(), requireEditReason);
        validateSaleHeader(sale);
        validateSaleItems(sale);

        List<SaleV2ComposeSunatRequest.CounterSaleSelection> selections = request.getCounterSales();
        if (selections == null || selections.isEmpty()) {
            throw new InvalidSaleV2Exception("Debe seleccionar al menos un counter-sale.");
        }

        Map<Long, SaleV2ComposeSunatRequest.SaleItemAdjustment> saleAdjustments = mapSaleAdjustments(request.getSaleItems());
        Map<Long, SaleV2ComposeSunatRequest.CounterSaleSelection> selectionsById = mapSelectionsById(selections);
        Map<Long, CounterSaleDetailResponse> detailById = mapCounterSaleDetails(counterSales);

        if (detailById.size() != selectionsById.size()) {
            Set<Long> missing = new LinkedHashSet<>(selectionsById.keySet());
            missing.removeAll(detailById.keySet());
            throw new InvalidSaleV2Exception("No se encontró el/los counter-sale seleccionado(s): " + missing);
        }

        List<SourceCounterSale> sources = new ArrayList<>();
        List<ComposedLine> lines = new ArrayList<>();
        int sequence = 1;

        for (SaleV2ItemResponse item : safeSaleItems(sale.getItems())) {
            BigDecimal composedUnitPrice = resolveSaleUnitPrice(item, saleAdjustments);
            lines.add(buildLine(
                    sequence++,
                    "SALE",
                    sale.getDocType() + " " + sale.getSeries() + "-" + sale.getNumber(),
                    sale.getSaleId(),
                    item.getSaleItemId(),
                    item.getLineNumber(),
                    item.getProductId(),
                    item.getSku(),
                    item.getDescription(),
                    item.getQuantity(),
                    item.getUnitPrice(),
                    composedUnitPrice,
                    nz(item.getDiscountPercent()),
                    nz(item.getRevenueTotal())
            ));
        }

        for (Long counterSaleId : selectionsById.keySet()) {
            CounterSaleDetailResponse detail = detailById.get(counterSaleId);
            validateCounterSale(detail);

            sources.add(SourceCounterSale.builder()
                    .counterSaleId(detail.getCounterSaleId())
                    .series(detail.getSeries())
                    .number(detail.getNumber())
                    .status(detail.getStatus())
                    .total(nz(detail.getTotal()).setScale(4, RoundingMode.HALF_UP))
                    .discountTotal(nz(detail.getDiscountTotal()).setScale(4, RoundingMode.HALF_UP))
                    .associatedToSunat(isCounterSaleAssociatedToSunat(detail))
                    .associatedDocType(readString(detail, "getAssociatedDocType"))
                    .associatedSeries(readString(detail, "getAssociatedSeries"))
                    .associatedNumber(readLong(detail, "getAssociatedNumber"))
                    .associatedAt(readLocalDateTime(detail, "getAssociatedAt"))
                    .build());

            Map<Long, SaleV2ComposeSunatRequest.CounterSaleItemAdjustment> itemAdjustments =
                    mapCounterSaleAdjustments(selectionsById.get(counterSaleId).getItems());

            for (CounterSaleItemResponse item : safeCounterSaleItems(detail.getItems(), detail.getCounterSaleId())) {
                validateCounterSaleItem(detail, item);
                BigDecimal composedUnitPrice = resolveCounterSaleUnitPrice(item, itemAdjustments);
                lines.add(buildLine(
                        sequence++,
                        "COUNTER_SALE",
                        "VENTANILLA " + detail.getSeries() + "-" + detail.getNumber(),
                        detail.getCounterSaleId(),
                        item.getCounterSaleItemId(),
                        item.getLineNumber(),
                        item.getProductId(),
                        item.getSku(),
                        item.getDescription(),
                        item.getQuantity(),
                        item.getUnitPrice(),
                        composedUnitPrice,
                        nz(item.getDiscountPercent()),
                        nz(item.getRevenueTotal())
                ));
            }
        }

        Totals totals = calculateTotals(
                nzs(sale.getTaxStatus()),
                Boolean.TRUE.equals(sale.getIgvIncluded()),
                nz(sale.getIgvRate(), DEFAULT_IGV_RATE),
                lines
        );
        BigDecimal originalSaleTotal = nz(sale.getTotal()).setScale(4, RoundingMode.HALF_UP);
        BigDecimal difference = totals.getTotal().subtract(originalSaleTotal).setScale(4, RoundingMode.HALF_UP);

        return CompositionPlan.builder()
                .saleId(sale.getSaleId())
                .docType(sale.getDocType())
                .series(sale.getSeries())
                .number(sale.getNumber())
                .taxStatus(sale.getTaxStatus())
                .igvRate(nz(sale.getIgvRate(), DEFAULT_IGV_RATE).setScale(2, RoundingMode.HALF_UP))
                .igvIncluded(Boolean.TRUE.equals(sale.getIgvIncluded()))
                .originalSaleTotal(originalSaleTotal)
                .totals(totals)
                .difference(difference)
                .exactTotalMatch(difference.compareTo(BigDecimal.ZERO.setScale(4, RoundingMode.HALF_UP)) == 0)
                .sources(sources)
                .lines(lines)
                .requestedEditReason(trimToNull(request.getEditReason()))
                .build();
    }

    public SaleV2AdminEditRequest buildAdminEditRequest(SaleV2DetailResponse sale,
                                                        CompositionPlan plan,
                                                        String paymentMethod) {
        List<SaleV2AdminEditRequest.Item> items = new ArrayList<>();
        for (ComposedLine line : plan.getLines()) {
            items.add(SaleV2AdminEditRequest.Item.builder()
                    .productId(line.getProductId())
                    .quantity(line.getQuantity())
                    .discountPercent(line.getDiscountPercent())
                    .lineKind(LineKind.VENDIDO)
                    .giftReason(null)
                    .unitPriceOverride(line.getComposedUnitPrice())
                    .serialUnitIds(List.of())
                    .build());
        }

        SaleV2AdminEditRequest.Payment payment = buildPaymentForAdminEdit(nzs(sale.getPaymentType()), paymentMethod);

        return SaleV2AdminEditRequest.builder()
                .issueDate(sale.getIssueDate())
                .priceList(null)
                .customerId(sale.getCustomerId())
                .customerDocType(sale.getCustomerDocType())
                .customerDocNumber(sale.getCustomerDocNumber())
                .customerName(sale.getCustomerName())
                .customerAddress(sale.getCustomerAddress())
                .taxStatus(TaxStatus.valueOf(nzs(sale.getTaxStatus()).toUpperCase()))
                .taxReason(sale.getTaxReason())
                .igvRate(sale.getIgvRate())
                .igvIncluded(sale.getIgvIncluded())
                .creditDays(sale.getCreditDays())
                .dueDate(sale.getDueDate())
                .notes(sale.getNotes())
                .editReason(plan.getRequestedEditReason())
                .items(items)
                .payment(payment)
                .build();
    }

    public SaleV2AdminEditRequest buildRestoreRequest(SaleV2DetailResponse sale, String editReason) {
        validateSaleHeader(sale);
        validateSaleItems(sale);

        List<SaleV2AdminEditRequest.Item> items = new ArrayList<>();
        for (SaleV2ItemResponse item : safeSaleItems(sale.getItems())) {
            List<Long> serialUnitIds = item.getSerialUnitId() == null ? List.of() : List.of(item.getSerialUnitId());
            items.add(SaleV2AdminEditRequest.Item.builder()
                    .productId(item.getProductId())
                    .quantity(item.getQuantity())
                    .discountPercent(nz(item.getDiscountPercent()).setScale(4, RoundingMode.HALF_UP))
                    .lineKind(LineKind.valueOf(nzs(item.getLineKind()).toUpperCase()))
                    .giftReason(item.getGiftReason())
                    .unitPriceOverride(nz(item.getUnitPrice()).setScale(4, RoundingMode.HALF_UP))
                    .serialUnitIds(serialUnitIds)
                    .build());
        }

        String paymentMethod = sale.getPayment() != null ? sale.getPayment().getMethod() : null;
        SaleV2AdminEditRequest.Payment payment = buildPaymentForAdminEdit(nzs(sale.getPaymentType()), paymentMethod);

        return SaleV2AdminEditRequest.builder()
                .issueDate(sale.getIssueDate())
                .priceList(null)
                .customerId(sale.getCustomerId())
                .customerDocType(sale.getCustomerDocType())
                .customerDocNumber(sale.getCustomerDocNumber())
                .customerName(sale.getCustomerName())
                .customerAddress(sale.getCustomerAddress())
                .taxStatus(TaxStatus.valueOf(nzs(sale.getTaxStatus()).toUpperCase()))
                .taxReason(sale.getTaxReason())
                .igvRate(sale.getIgvRate())
                .igvIncluded(sale.getIgvIncluded())
                .creditDays(sale.getCreditDays())
                .dueDate(sale.getDueDate())
                .notes(sale.getNotes())
                .editReason(editReason)
                .items(items)
                .payment(payment)
                .build();
    }

    private SaleV2AdminEditRequest.Payment buildPaymentForAdminEdit(String paymentType, String paymentMethod) {
        if (!"CONTADO".equalsIgnoreCase(paymentType)) {
            return null;
        }
        if (paymentMethod == null || paymentMethod.isBlank()) {
            throw new InvalidSaleV2Exception("No se pudo determinar payment.method de la venta original.");
        }
        try {
            return SaleV2AdminEditRequest.Payment.builder()
                    .method(PaymentMethod.valueOf(paymentMethod.trim().toUpperCase()))
                    .build();
        } catch (IllegalArgumentException ex) {
            throw new InvalidSaleV2Exception("payment.method no soportado para restauración/composición: " + paymentMethod);
        }
    }

    private void validateEditReason(String editReason, boolean requireEditReason) {
        String normalized = trimToNull(editReason);
        if (requireEditReason && normalized == null) {
            throw new InvalidSaleV2Exception("editReason es obligatorio.");
        }
        if (normalized != null && normalized.length() > 500) {
            throw new InvalidSaleV2Exception("editReason no puede exceder 500 caracteres.");
        }
    }

    private void validateSaleHeader(SaleV2DetailResponse sale) {
        String docType = nzs(sale.getDocType()).toUpperCase();
        if (!"BOLETA".equals(docType) && !"FACTURA".equals(docType)) {
            throw new InvalidSaleV2Exception("Solo BOLETA/FACTURA permiten composición SUNAT con counter-sale. docType=" + sale.getDocType());
        }
        if (!"EMITIDA".equalsIgnoreCase(nzs(sale.getStatus()))) {
            throw new InvalidSaleV2Exception("La venta base debe estar EMITIDA. Estado actual: " + sale.getStatus());
        }
        String sunatStatus = nzs(sale.getSunat() != null ? sale.getSunat().getStatus() : null).toUpperCase();
        if (!(sunatStatus.isBlank() || "NO_ENVIADO".equals(sunatStatus) || "ERROR".equals(sunatStatus) || "RECHAZADO".equals(sunatStatus))) {
            throw new InvalidSaleV2Exception("La venta base ya no está disponible para composición previa a SUNAT. sunatStatus=" + sunatStatus);
        }
    }

    private void validateSaleItems(SaleV2DetailResponse sale) {
        List<SaleV2ItemResponse> items = safeSaleItems(sale.getItems());
        if (items.isEmpty()) {
            throw new InvalidSaleV2Exception("La venta base no tiene items.");
        }
        for (SaleV2ItemResponse item : items) {
            if (item.getSerialUnitId() != null) {
                throw new InvalidSaleV2Exception("La venta base contiene productos serializados y no puede usar composición SUNAT. saleItemId=" + item.getSaleItemId());
            }
            if (!Boolean.TRUE.equals(item.getVisibleInDocument())) {
                throw new InvalidSaleV2Exception("La venta base contiene líneas no visibles para SUNAT. saleItemId=" + item.getSaleItemId());
            }
            if (!"VENDIDO".equalsIgnoreCase(nzs(item.getLineKind()))) {
                throw new InvalidSaleV2Exception("La composición SUNAT solo soporta líneas VENDIDO. saleItemId=" + item.getSaleItemId());
            }
            if (nz(item.getDiscountPercent()).compareTo(BigDecimal.ZERO) != 0) {
                throw new InvalidSaleV2Exception("La composición SUNAT no soporta discountPercent distinto de 0. saleItemId=" + item.getSaleItemId());
            }
            if (!Boolean.TRUE.equals(item.getFacturableSunat())) {
                throw new InvalidSaleV2Exception("La venta base contiene un producto no facturable a SUNAT. saleItemId=" + item.getSaleItemId());
            }
        }
    }

    private void validateCounterSale(CounterSaleDetailResponse detail) {
        if (detail == null) {
            throw new InvalidSaleV2Exception("Counter-sale no encontrado.");
        }
        if (!"EMITIDA".equalsIgnoreCase(nzs(detail.getStatus()))) {
            throw new InvalidSaleV2Exception("El counter-sale debe estar EMITIDA. counterSaleId=" + detail.getCounterSaleId());
        }
        if (isCounterSaleAssociatedToSunat(detail)) {
            throw new InvalidSaleV2Exception("El counter-sale ya está asociado a SUNAT. counterSaleId=" + detail.getCounterSaleId());
        }
    }

    private void validateCounterSaleItem(CounterSaleDetailResponse detail, CounterSaleItemResponse item) {
        if (!"VENDIDO".equalsIgnoreCase(nzs(item.getLineKind()))) {
            throw new InvalidSaleV2Exception("La composición SUNAT solo soporta líneas VENDIDO en counter-sale. counterSaleItemId=" + item.getCounterSaleItemId());
        }
        if (nz(item.getDiscountPercent()).compareTo(BigDecimal.ZERO) != 0) {
            throw new InvalidSaleV2Exception("La composición SUNAT no soporta discountPercent distinto de 0 en counter-sale. counterSaleItemId=" + item.getCounterSaleItemId());
        }
        if (item.getSerialUnits() != null && !item.getSerialUnits().isEmpty()) {
            throw new InvalidSaleV2Exception(
                    "El counter-sale contiene productos serializados y no puede usarse en composición SUNAT. counterSaleId="
                            + detail.getCounterSaleId() + ", counterSaleItemId=" + item.getCounterSaleItemId()
            );
        }
        ProductSnapshot snapshot = productSnapshotRepository.findSnapshotById(item.getProductId());
        if (snapshot == null) {
            throw new InvalidSaleV2Exception("Producto no encontrado para counterSaleItemId=" + item.getCounterSaleItemId());
        }
        if (!Boolean.TRUE.equals(snapshot.getFacturableSunat())) {
            throw new InvalidSaleV2Exception("El producto del counter-sale no es facturable para SUNAT. productId=" + item.getProductId());
        }
    }

    private List<SaleV2ItemResponse> safeSaleItems(List<SaleV2ItemResponse> items) {
        return items == null ? List.of() : items;
    }

    private List<CounterSaleItemResponse> safeCounterSaleItems(List<CounterSaleItemResponse> items, Long counterSaleId) {
        List<CounterSaleItemResponse> safe = items == null ? List.of() : items;
        if (safe.isEmpty()) {
            throw new InvalidSaleV2Exception("El counter-sale no tiene items. counterSaleId=" + counterSaleId);
        }
        return safe;
    }

    private Map<Long, CounterSaleDetailResponse> mapCounterSaleDetails(List<CounterSaleDetailResponse> counterSales) {
        Map<Long, CounterSaleDetailResponse> map = new LinkedHashMap<>();
        if (counterSales == null) {
            return map;
        }
        for (CounterSaleDetailResponse detail : counterSales) {
            if (detail != null && detail.getCounterSaleId() != null) {
                map.put(detail.getCounterSaleId(), detail);
            }
        }
        return map;
    }

    private BigDecimal resolveSaleUnitPrice(SaleV2ItemResponse item,
                                            Map<Long, SaleV2ComposeSunatRequest.SaleItemAdjustment> saleAdjustments) {
        BigDecimal composedUnitPrice = nz(item.getUnitPrice()).setScale(4, RoundingMode.HALF_UP);
        SaleV2ComposeSunatRequest.SaleItemAdjustment adjustment = saleAdjustments.get(item.getSaleItemId());
        if (adjustment != null && adjustment.getUnitPriceOverride() != null) {
            validateUnitPriceOverride(item.getUnitPrice(), adjustment.getUnitPriceOverride(), "saleItemId=" + item.getSaleItemId());
            composedUnitPrice = adjustment.getUnitPriceOverride().setScale(4, RoundingMode.HALF_UP);
        }
        return composedUnitPrice;
    }

    private BigDecimal resolveCounterSaleUnitPrice(CounterSaleItemResponse item,
                                                   Map<Long, SaleV2ComposeSunatRequest.CounterSaleItemAdjustment> itemAdjustments) {
        BigDecimal composedUnitPrice = nz(item.getUnitPrice()).setScale(4, RoundingMode.HALF_UP);
        SaleV2ComposeSunatRequest.CounterSaleItemAdjustment adjustment = itemAdjustments.get(item.getCounterSaleItemId());
        if (adjustment != null && adjustment.getUnitPriceOverride() != null) {
            validateUnitPriceOverride(item.getUnitPrice(), adjustment.getUnitPriceOverride(), "counterSaleItemId=" + item.getCounterSaleItemId());
            composedUnitPrice = adjustment.getUnitPriceOverride().setScale(4, RoundingMode.HALF_UP);
        }
        return composedUnitPrice;
    }

    private Map<Long, SaleV2ComposeSunatRequest.SaleItemAdjustment> mapSaleAdjustments(List<SaleV2ComposeSunatRequest.SaleItemAdjustment> adjustments) {
        Map<Long, SaleV2ComposeSunatRequest.SaleItemAdjustment> map = new LinkedHashMap<>();
        if (adjustments == null) {
            return map;
        }
        for (SaleV2ComposeSunatRequest.SaleItemAdjustment adjustment : adjustments) {
            if (adjustment == null || adjustment.getSaleItemId() == null) {
                continue;
            }
            if (map.putIfAbsent(adjustment.getSaleItemId(), adjustment) != null) {
                throw new InvalidSaleV2Exception("saleItemId repetido en request: " + adjustment.getSaleItemId());
            }
        }
        return map;
    }

    private Map<Long, SaleV2ComposeSunatRequest.CounterSaleSelection> mapSelectionsById(List<SaleV2ComposeSunatRequest.CounterSaleSelection> selections) {
        Map<Long, SaleV2ComposeSunatRequest.CounterSaleSelection> map = new LinkedHashMap<>();
        for (SaleV2ComposeSunatRequest.CounterSaleSelection selection : selections) {
            if (selection == null || selection.getCounterSaleId() == null) {
                throw new InvalidSaleV2Exception("counterSaleId es obligatorio en la selección.");
            }
            if (map.putIfAbsent(selection.getCounterSaleId(), selection) != null) {
                throw new InvalidSaleV2Exception("counterSaleId repetido en request: " + selection.getCounterSaleId());
            }
        }
        return map;
    }

    private Map<Long, SaleV2ComposeSunatRequest.CounterSaleItemAdjustment> mapCounterSaleAdjustments(List<SaleV2ComposeSunatRequest.CounterSaleItemAdjustment> adjustments) {
        Map<Long, SaleV2ComposeSunatRequest.CounterSaleItemAdjustment> map = new LinkedHashMap<>();
        if (adjustments == null) {
            return map;
        }
        for (SaleV2ComposeSunatRequest.CounterSaleItemAdjustment adjustment : adjustments) {
            if (adjustment == null || adjustment.getCounterSaleItemId() == null) {
                continue;
            }
            if (map.putIfAbsent(adjustment.getCounterSaleItemId(), adjustment) != null) {
                throw new InvalidSaleV2Exception("counterSaleItemId repetido en request: " + adjustment.getCounterSaleItemId());
            }
        }
        return map;
    }

    private void validateUnitPriceOverride(BigDecimal original, BigDecimal override, String label) {
        BigDecimal originalValue = nz(original).setScale(4, RoundingMode.HALF_UP);
        BigDecimal overrideValue = nz(override).setScale(4, RoundingMode.HALF_UP);
        if (overrideValue.compareTo(BigDecimal.ZERO) < 0) {
            throw new InvalidSaleV2Exception("unitPriceOverride no puede ser negativo. " + label);
        }
        if (overrideValue.compareTo(originalValue) > 0) {
            throw new InvalidSaleV2Exception("unitPriceOverride solo puede mantener o bajar el precio. " + label);
        }
    }

    private ComposedLine buildLine(int sequence,
                                   String sourceType,
                                   String sourceDocumentLabel,
                                   Long sourceDocumentId,
                                   Long sourceItemId,
                                   Integer sourceLineNumber,
                                   Long productId,
                                   String sku,
                                   String description,
                                   BigDecimal quantity,
                                   BigDecimal originalUnitPrice,
                                   BigDecimal composedUnitPrice,
                                   BigDecimal discountPercent,
                                   BigDecimal originalRevenueTotal) {
        BigDecimal qty = nz(quantity).setScale(3, RoundingMode.HALF_UP);
        BigDecimal unitOriginal = nz(originalUnitPrice).setScale(4, RoundingMode.HALF_UP);
        BigDecimal unitComposed = nz(composedUnitPrice).setScale(4, RoundingMode.HALF_UP);
        BigDecimal pct = nz(discountPercent).setScale(4, RoundingMode.HALF_UP);

        BigDecimal gross = qty.multiply(unitComposed).setScale(4, RoundingMode.HALF_UP);
        BigDecimal discountAmount = gross.multiply(pct).divide(ONE_HUNDRED, 4, RoundingMode.HALF_UP);
        BigDecimal grossAfterDiscount = gross.subtract(discountAmount).setScale(4, RoundingMode.HALF_UP);

        return ComposedLine.builder()
                .sequence(sequence)
                .sourceType(sourceType)
                .sourceDocumentLabel(sourceDocumentLabel)
                .sourceDocumentId(sourceDocumentId)
                .sourceItemId(sourceItemId)
                .sourceLineNumber(sourceLineNumber)
                .productId(productId)
                .sku(sku)
                .description(description)
                .quantity(qty)
                .originalUnitPrice(unitOriginal)
                .composedUnitPrice(unitComposed)
                .discountPercent(pct)
                .originalRevenueTotal(nz(originalRevenueTotal).setScale(4, RoundingMode.HALF_UP))
                .composedRevenueTotal(grossAfterDiscount)
                .build();
    }

    private Totals calculateTotals(String taxStatus,
                                   boolean igvIncluded,
                                   BigDecimal igvRate,
                                   List<ComposedLine> lines) {
        BigDecimal subtotal = BigDecimal.ZERO;
        BigDecimal discountTotal = BigDecimal.ZERO;
        BigDecimal igvAmount = BigDecimal.ZERO;
        BigDecimal total = BigDecimal.ZERO;

        for (ComposedLine line : lines) {
            BigDecimal gross = line.getQuantity().multiply(line.getComposedUnitPrice()).setScale(4, RoundingMode.HALF_UP);
            BigDecimal lineDiscount = gross.multiply(line.getDiscountPercent()).divide(ONE_HUNDRED, 4, RoundingMode.HALF_UP);
            BigDecimal grossAfterDiscount = gross.subtract(lineDiscount).setScale(4, RoundingMode.HALF_UP);

            BigDecimal baseLine = grossAfterDiscount;
            BigDecimal igvLine = BigDecimal.ZERO;
            if ("GRAVADA".equalsIgnoreCase(taxStatus) && igvIncluded) {
                BigDecimal divisor = BigDecimal.ONE.add(igvRate.divide(ONE_HUNDRED, 10, RoundingMode.HALF_UP));
                baseLine = grossAfterDiscount.divide(divisor, 4, RoundingMode.HALF_UP);
                igvLine = grossAfterDiscount.subtract(baseLine).setScale(4, RoundingMode.HALF_UP);
            }

            subtotal = subtotal.add(baseLine);
            discountTotal = discountTotal.add(lineDiscount);
            if (igvIncluded) {
                igvAmount = igvAmount.add(igvLine);
                total = total.add(grossAfterDiscount);
            } else {
                total = total.add(baseLine);
            }
        }

        subtotal = subtotal.setScale(4, RoundingMode.HALF_UP);
        discountTotal = discountTotal.setScale(4, RoundingMode.HALF_UP);

        if ("GRAVADA".equalsIgnoreCase(taxStatus) && !igvIncluded) {
            igvAmount = subtotal.multiply(igvRate).divide(ONE_HUNDRED, 4, RoundingMode.HALF_UP);
            total = subtotal.add(igvAmount).setScale(4, RoundingMode.HALF_UP);
        } else if (!"GRAVADA".equalsIgnoreCase(taxStatus)) {
            igvAmount = BigDecimal.ZERO.setScale(4, RoundingMode.HALF_UP);
            total = subtotal.setScale(4, RoundingMode.HALF_UP);
        } else {
            igvAmount = igvAmount.setScale(4, RoundingMode.HALF_UP);
            total = total.setScale(4, RoundingMode.HALF_UP);
        }

        return Totals.builder()
                .subtotal(subtotal)
                .discountTotal(discountTotal)
                .igvAmount(igvAmount)
                .total(total)
                .build();
    }


    private boolean isCounterSaleAssociatedToSunat(CounterSaleDetailResponse detail) {
        return Boolean.TRUE.equals(readBoolean(detail, "getAssociatedToSunat"));
    }

    private Boolean readBoolean(Object target, String methodName) {
        try {
            Object value = target.getClass().getMethod(methodName).invoke(target);
            return value instanceof Boolean b ? b : null;
        } catch (Exception ignored) {
            return null;
        }
    }

    private String readString(Object target, String methodName) {
        try {
            Object value = target.getClass().getMethod(methodName).invoke(target);
            return value != null ? value.toString() : null;
        } catch (Exception ignored) {
            return null;
        }
    }

    private Long readLong(Object target, String methodName) {
        try {
            Object value = target.getClass().getMethod(methodName).invoke(target);
            if (value instanceof Number number) {
                return number.longValue();
            }
            return null;
        } catch (Exception ignored) {
            return null;
        }
    }

    private java.time.LocalDateTime readLocalDateTime(Object target, String methodName) {
        try {
            Object value = target.getClass().getMethod(methodName).invoke(target);
            return value instanceof java.time.LocalDateTime ldt ? ldt : null;
        } catch (Exception ignored) {
            return null;
        }
    }

    private BigDecimal nz(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private BigDecimal nz(BigDecimal value, BigDecimal defaultValue) {
        return value == null ? defaultValue : value;
    }

    private String nzs(String value) {
        return value == null ? "" : value;
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class CompositionPlan {
        private Long saleId;
        private String docType;
        private String series;
        private Long number;
        private String taxStatus;
        private BigDecimal igvRate;
        private boolean igvIncluded;
        private BigDecimal originalSaleTotal;
        private Totals totals;
        private BigDecimal difference;
        private boolean exactTotalMatch;
        private List<SourceCounterSale> sources;
        private List<ComposedLine> lines;
        private String requestedEditReason;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class SourceCounterSale {
        private Long counterSaleId;
        private String series;
        private Long number;
        private String status;
        private BigDecimal total;
        private BigDecimal discountTotal;
        private Boolean associatedToSunat;
        private String associatedDocType;
        private String associatedSeries;
        private Long associatedNumber;
        private java.time.LocalDateTime associatedAt;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ComposedLine {
        private Integer sequence;
        private String sourceType;
        private String sourceDocumentLabel;
        private Long sourceDocumentId;
        private Long sourceItemId;
        private Integer sourceLineNumber;
        private Long productId;
        private String sku;
        private String description;
        private BigDecimal quantity;
        private BigDecimal originalUnitPrice;
        private BigDecimal composedUnitPrice;
        private BigDecimal discountPercent;
        private BigDecimal originalRevenueTotal;
        private BigDecimal composedRevenueTotal;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class Totals {
        private BigDecimal subtotal;
        private BigDecimal discountTotal;
        private BigDecimal igvAmount;
        private BigDecimal total;
    }
}
