package com.paulfernandosr.possystembackend.countersale.application;

import com.paulfernandosr.possystembackend.countersale.domain.exception.InvalidCounterSaleException;
import com.paulfernandosr.possystembackend.countersale.domain.model.*;
import com.paulfernandosr.possystembackend.countersale.domain.port.input.CreateCounterSaleUseCase;
import com.paulfernandosr.possystembackend.countersale.domain.port.output.*;
import com.paulfernandosr.possystembackend.countersale.infrastructure.adapter.input.dto.CounterSaleCreateRequest;
import com.paulfernandosr.possystembackend.countersale.infrastructure.adapter.input.dto.CounterSaleDocumentResponse;
import com.paulfernandosr.possystembackend.user.domain.User;
import com.paulfernandosr.possystembackend.user.domain.port.output.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.*;

@Service
@RequiredArgsConstructor
public class CreateCounterSaleService implements CreateCounterSaleUseCase {

    private static final String DOCUMENT_SERIES_TYPE = "VENTANILLA";

    private final UserRepository userRepository;
    private final DocumentSeriesRepository documentSeriesRepository;
    private final ProductSnapshotRepository productSnapshotRepository;
    private final CounterSaleRepository counterSaleRepository;
    private final CounterSalePaymentRepository counterSalePaymentRepository;
    private final ProductStockRepository productStockRepository;
    private final ProductStockMovementRepository productStockMovementRepository;
    private final ProductSerialUnitRepository productSerialUnitRepository;
    private final SaleSessionControlRepository saleSessionControlRepository;
    private final SaleSessionAccumulatorRepository saleSessionAccumulatorRepository;

    private final CostPolicy costPolicy = CostPolicy.PROMEDIO;

    @Override
    @Transactional
    public CounterSaleDocumentResponse create(CounterSaleCreateRequest request, String username) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new InvalidCounterSaleException("Usuario inválido: " + username));

        if (user.isNotOnRegister()) {
            throw new InvalidCounterSaleException("El usuario no tiene una sesión de caja abierta.");
        }

        validateRequest(request);

        OpenSaleSession openSession = saleSessionControlRepository.findOpenByUserId(user.getId());
        if (openSession == null) {
            throw new InvalidCounterSaleException("El usuario no tiene una sesión de caja abierta.");
        }
        if (!Objects.equals(openSession.getStationId(), request.getStationId())) {
            throw new InvalidCounterSaleException("La estación no coincide con la sesión de caja abierta del usuario.");
        }
        if (request.getSaleSessionId() != null && !Objects.equals(request.getSaleSessionId(), openSession.getId())) {
            throw new InvalidCounterSaleException("saleSessionId no coincide con la sesión de caja abierta del usuario.");
        }

        LocalDate issueDate = request.getIssueDate() != null ? request.getIssueDate() : LocalDate.now();
        CounterSaleTaxStatus taxStatus = request.getTaxStatus() == null ? CounterSaleTaxStatus.GRAVADA : request.getTaxStatus();
        boolean igvIncluded = Boolean.TRUE.equals(request.getIgvIncluded()) && taxStatus == CounterSaleTaxStatus.GRAVADA;
        BigDecimal igvRate = nz(request.getIgvRate(), new BigDecimal("18.00"));

        LockedDocumentSeries locked = documentSeriesRepository.lockSeries(DOCUMENT_SERIES_TYPE, request.getSeries());
        Long number = locked.getNextNumber();

        List<ComputedLine> computedLines = new ArrayList<>();
        int lineNumber = 1;

        for (CounterSaleCreateRequest.Item item : request.getItems()) {
            ProductSnapshot product = productSnapshotRepository.findSnapshotById(item.getProductId());
            if (product == null) {
                throw new InvalidCounterSaleException("Producto no encontrado: " + item.getProductId());
            }

            BigDecimal quantity = nz(item.getQuantity());
            if (quantity.compareTo(BigDecimal.ZERO) <= 0) {
                throw new InvalidCounterSaleException("Cantidad inválida para productId=" + item.getProductId());
            }

            CounterSaleLineKind lineKind = item.getLineKind() != null ? item.getLineKind() : CounterSaleLineKind.VENDIDO;

            if (lineKind == CounterSaleLineKind.OBSEQUIO) {
                if (item.getGiftReason() == null || item.getGiftReason().trim().isEmpty()) {
                    throw new InvalidCounterSaleException("giftReason es obligatorio para obsequios (productId=" + item.getProductId() + ")");
                }
                if (Boolean.FALSE.equals(product.getGiftAllowed())) {
                    throw new InvalidCounterSaleException("El producto no permite obsequio: " + nzs(product.getSku()));
                }
            }

            if (Boolean.TRUE.equals(product.getManageBySerial())) {
                int qtyInt;
                try {
                    qtyInt = quantity.intValueExact();
                } catch (ArithmeticException ex) {
                    throw new InvalidCounterSaleException("Cantidad debe ser entera para productos con serie/VIN. productId=" + item.getProductId());
                }

                List<Long> serialIds = item.getSerialUnitIds();
                if (serialIds == null || serialIds.size() != qtyInt) {
                    throw new InvalidCounterSaleException("Debe seleccionar exactamente " + qtyInt + " unidades serializadas para productId=" + item.getProductId());
                }

                List<SerialUnit> units = productSerialUnitRepository.lockByIds(serialIds);
                if (units.size() != serialIds.size()) {
                    throw new InvalidCounterSaleException("Alguna(s) unidades serializadas no existen o no están disponibles.");
                }

                Set<Long> uniqueIds = new HashSet<>();
                for (SerialUnit unit : units) {
                    if (!uniqueIds.add(unit.getId())) {
                        throw new InvalidCounterSaleException("No se puede repetir una unidad serializada. serialUnitId=" + unit.getId());
                    }
                    if (!Objects.equals(unit.getProductId(), product.getId())) {
                        throw new InvalidCounterSaleException("Unidad serializada no pertenece al producto. serialUnitId=" + unit.getId());
                    }
                    if (!"EN_ALMACEN".equalsIgnoreCase(nzs(unit.getStatus()))) {
                        throw new InvalidCounterSaleException("Unidad serializada no disponible (status=" + unit.getStatus() + "). serialUnitId=" + unit.getId());
                    }
                }

                if (Boolean.FALSE.equals(product.getAffectsStock())) {
                    throw new InvalidCounterSaleException("Producto serializado debe afectar stock. productId=" + product.getId());
                }
            } else if (item.getSerialUnitIds() != null && !item.getSerialUnitIds().isEmpty()) {
                throw new InvalidCounterSaleException("serialUnitIds solo aplica para productos con manage_by_serial=true. productId=" + item.getProductId());
            }

            BigDecimal manualUnitPrice = item.resolveUnitPrice();
            BigDecimal unitPrice = manualUnitPrice != null
                    ? manualUnitPrice
                    : product.priceFor(request.getPriceList());
            unitPrice = nz(unitPrice);
            if (unitPrice.compareTo(BigDecimal.ZERO) < 0) {
                throw new InvalidCounterSaleException("unitPrice no puede ser negativo. productId=" + item.getProductId());
            }
            if (lineKind == CounterSaleLineKind.OBSEQUIO) {
                unitPrice = BigDecimal.ZERO;
            } else if (unitPrice.compareTo(BigDecimal.ZERO) == 0) {
                throw new InvalidCounterSaleException("unitPrice debe ser mayor a 0 para líneas vendidas. productId=" + item.getProductId());
            }

            BigDecimal discountPercent = nz(item.getDiscountPercent());
            if (discountPercent.compareTo(BigDecimal.ZERO) < 0 || discountPercent.compareTo(new BigDecimal("100")) > 0) {
                throw new InvalidCounterSaleException("discountPercent debe estar entre 0 y 100. productId=" + item.getProductId());
            }

            BigDecimal gross = quantity.multiply(unitPrice);
            BigDecimal discountAmount = lineKind == CounterSaleLineKind.OBSEQUIO
                    ? BigDecimal.ZERO
                    : gross.multiply(discountPercent).divide(new BigDecimal("100"), 4, RoundingMode.HALF_UP);

            BigDecimal grossAfterDiscount = lineKind == CounterSaleLineKind.OBSEQUIO
                    ? BigDecimal.ZERO
                    : gross.subtract(discountAmount).setScale(4, RoundingMode.HALF_UP);

            BigDecimal baseLine = grossAfterDiscount;
            BigDecimal igvLine = BigDecimal.ZERO;
            if (taxStatus == CounterSaleTaxStatus.GRAVADA && igvIncluded && lineKind != CounterSaleLineKind.OBSEQUIO) {
                BigDecimal divisor = BigDecimal.ONE.add(igvRate.divide(new BigDecimal("100"), 10, RoundingMode.HALF_UP));
                baseLine = grossAfterDiscount.divide(divisor, 4, RoundingMode.HALF_UP);
                igvLine = grossAfterDiscount.subtract(baseLine).setScale(4, RoundingMode.HALF_UP);
            }

            BigDecimal unitCostSnapshot = null;
            BigDecimal totalCostSnapshot = null;
            boolean affectsStock = Boolean.TRUE.equals(product.getAffectsStock());
            if (affectsStock) {
                unitCostSnapshot = costPolicy == CostPolicy.PROMEDIO
                        ? nz(productStockRepository.getAverageCost(product.getId()))
                        : nz(productStockRepository.getLastUnitCost(product.getId()));
                totalCostSnapshot = unitCostSnapshot.multiply(quantity).setScale(4, RoundingMode.HALF_UP);
            }

            computedLines.add(ComputedLine.builder()
                    .lineNumber(lineNumber++)
                    .product(product)
                    .quantity(quantity)
                    .unitPrice(unitPrice.setScale(4, RoundingMode.HALF_UP))
                    .discountPercent(discountPercent.setScale(4, RoundingMode.HALF_UP))
                    .discountAmount(discountAmount.setScale(4, RoundingMode.HALF_UP))
                    .lineKind(lineKind)
                    .giftReason(item.getGiftReason())
                    .unitCostSnapshot(unitCostSnapshot)
                    .totalCostSnapshot(totalCostSnapshot)
                    .revenueTotal(baseLine.setScale(4, RoundingMode.HALF_UP))
                    .igvLine(igvLine.setScale(4, RoundingMode.HALF_UP))
                    .grossAfterDiscount(grossAfterDiscount)
                    .serialUnitIds(item.getSerialUnitIds())
                    .build());
        }

        Totals totals = calculateTotals(taxStatus, igvIncluded, igvRate, computedLines);
        validateDocumentRules(request, totals);

        Long counterSaleId = counterSaleRepository.insertCounterSale(
                request.getStationId(),
                openSession.getId(),
                user.getId(),
                request.getSeries().trim().toUpperCase(),
                number,
                issueDate,
                nzs(request.getCurrency(), "PEN"),
                request.getExchangeRate(),
                request.getPriceList().name(),
                request.getCustomerId(),
                trimToNull(request.getCustomerDocType()),
                trimToNull(request.getCustomerDocNumber()),
                trimToNull(request.getCustomerName()),
                trimToNull(request.getCustomerAddress()),
                taxStatus.name(),
                igvRate,
                igvIncluded,
                trimToNull(request.getNotes())
        );

        for (ComputedLine line : computedLines) {
            Long counterSaleItemId = counterSaleRepository.insertCounterSaleItem(
                    counterSaleId,
                    line.getLineNumber(),
                    line.getProduct().getId(),
                    nzs(line.getProduct().getSku()),
                    nzs(line.getProduct().getName()),
                    line.getProduct().getPresentation(),
                    line.getProduct().getFactor(),
                    line.getQuantity(),
                    line.getUnitPrice(),
                    line.getDiscountPercent(),
                    line.getDiscountAmount(),
                    line.getLineKind().name(),
                    line.getGiftReason(),
                    Boolean.TRUE.equals(line.getProduct().getAffectsStock()),
                    line.getUnitCostSnapshot(),
                    line.getTotalCostSnapshot(),
                    line.getRevenueTotal()
            );

            if (Boolean.TRUE.equals(line.getProduct().getAffectsStock())) {
                productStockRepository.decreaseOnHandOrFail(line.getProduct().getId(), line.getQuantity());
                productStockMovementRepository.createOutCounterSale(line.getProduct().getId(), line.getQuantity(), counterSaleItemId);
            }

            if (Boolean.TRUE.equals(line.getProduct().getManageBySerial()) && line.getSerialUnitIds() != null) {
                for (Long serialUnitId : line.getSerialUnitIds()) {
                    productSerialUnitRepository.markAsSold(serialUnitId);
                    counterSaleRepository.linkSerialUnit(counterSaleItemId, serialUnitId);
                }
            }
        }

        counterSaleRepository.updateTotals(
                counterSaleId,
                totals.subtotal,
                totals.discountTotal,
                totals.igvAmount,
                totals.total,
                totals.giftCostTotal
        );

        counterSalePaymentRepository.insert(counterSaleId, request.getPayment().getMethod().name(), totals.total);
        saleSessionAccumulatorRepository.addSaleIncomeAndDiscount(openSession.getId(), totals.total, totals.discountTotal);
        documentSeriesRepository.incrementNextNumber(locked.getId());

        return CounterSaleDocumentResponse.builder()
                .counterSaleId(counterSaleId)
                .series(request.getSeries().trim().toUpperCase())
                .number(number)
                .issueDate(issueDate)
                .subtotal(totals.subtotal)
                .discountTotal(totals.discountTotal)
                .igvAmount(totals.igvAmount)
                .total(totals.total)
                .giftCostTotal(totals.giftCostTotal)
                .paymentMethod(request.getPayment().getMethod())
                .build();
    }

    private void validateDocumentRules(CounterSaleCreateRequest request, Totals totals) {
        if (totals.total != null && totals.total.compareTo(new BigDecimal("700")) >= 0) {
            if (isBlank(request.getCustomerDocType()) || isBlank(request.getCustomerDocNumber())) {
                throw new InvalidCounterSaleException("Operaciones >= 700 requieren customerDocType y customerDocNumber.");
            }
        }
    }

    private void validateRequest(CounterSaleCreateRequest request) {
        if (request == null) throw new InvalidCounterSaleException("Request vacío.");
        if (request.getStationId() == null) throw new InvalidCounterSaleException("stationId es obligatorio.");
        if (isBlank(request.getSeries())) throw new InvalidCounterSaleException("series es obligatorio.");
        boolean hasMissingResolvedPrice = request.getItems() != null && request.getItems().stream()
                .anyMatch(item -> item != null && item.resolveUnitPrice() == null);
        if (request.getPriceList() == null && hasMissingResolvedPrice) {
            throw new InvalidCounterSaleException("priceList es obligatorio cuando alguna línea no envía unitPrice.");
        }
        if (request.getItems() == null || request.getItems().isEmpty()) throw new InvalidCounterSaleException("Debe enviar items.");
        if (request.getPayment() == null || request.getPayment().getMethod() == null) {
            throw new InvalidCounterSaleException("payment.method es obligatorio.");
        }
        if (request.getTaxStatus() == null) request.setTaxStatus(CounterSaleTaxStatus.GRAVADA);
        if (request.getIgvIncluded() == null) request.setIgvIncluded(Boolean.FALSE);
        if (request.getTaxStatus() != CounterSaleTaxStatus.GRAVADA) request.setIgvIncluded(Boolean.FALSE);
        if (request.getCurrency() == null || request.getCurrency().isBlank()) request.setCurrency("PEN");
        request.setCurrency(request.getCurrency().trim().toUpperCase());
        if (!"PEN".equals(request.getCurrency()) && request.getExchangeRate() == null) {
            throw new InvalidCounterSaleException("exchangeRate es obligatorio cuando currency es distinta de PEN.");
        }
        request.setSeries(request.getSeries().trim().toUpperCase());
    }

    private Totals calculateTotals(CounterSaleTaxStatus taxStatus,
                                   boolean igvIncluded,
                                   BigDecimal igvRate,
                                   List<ComputedLine> lines) {
        BigDecimal subtotal = BigDecimal.ZERO;
        BigDecimal discountTotal = BigDecimal.ZERO;
        BigDecimal giftCostTotal = BigDecimal.ZERO;
        BigDecimal igvAmount = BigDecimal.ZERO;
        BigDecimal total = BigDecimal.ZERO;

        for (ComputedLine line : lines) {
            subtotal = subtotal.add(line.getRevenueTotal());
            discountTotal = discountTotal.add(line.getDiscountAmount());
            if (line.getLineKind() == CounterSaleLineKind.OBSEQUIO && line.getTotalCostSnapshot() != null) {
                giftCostTotal = giftCostTotal.add(line.getTotalCostSnapshot());
            }
            if (igvIncluded) {
                igvAmount = igvAmount.add(line.getIgvLine());
                total = total.add(line.getGrossAfterDiscount());
            } else {
                total = total.add(line.getRevenueTotal());
            }
        }

        subtotal = subtotal.setScale(4, RoundingMode.HALF_UP);
        discountTotal = discountTotal.setScale(4, RoundingMode.HALF_UP);
        giftCostTotal = giftCostTotal.setScale(4, RoundingMode.HALF_UP);

        if (taxStatus == CounterSaleTaxStatus.GRAVADA && !igvIncluded) {
            igvAmount = subtotal.multiply(igvRate).divide(new BigDecimal("100"), 4, RoundingMode.HALF_UP);
            total = subtotal.add(igvAmount).setScale(4, RoundingMode.HALF_UP);
        } else if (taxStatus != CounterSaleTaxStatus.GRAVADA) {
            igvAmount = BigDecimal.ZERO;
            total = subtotal.setScale(4, RoundingMode.HALF_UP);
        } else {
            igvAmount = igvAmount.setScale(4, RoundingMode.HALF_UP);
            total = total.setScale(4, RoundingMode.HALF_UP);
        }

        return new Totals(subtotal, discountTotal, igvAmount, total, giftCostTotal);
    }

    private static BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }

    private static BigDecimal nz(BigDecimal v, BigDecimal def) {
        return v == null ? def : v;
    }

    private static String nzs(String v) {
        return v == null ? "" : v;
    }

    private static String nzs(String v, String def) {
        return (v == null || v.trim().isEmpty()) ? def : v;
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private static String trimToNull(String value) {
        return isBlank(value) ? null : value.trim();
    }

    @lombok.Builder
    @lombok.Getter
    private static class ComputedLine {
        private Integer lineNumber;
        private ProductSnapshot product;
        private BigDecimal quantity;
        private BigDecimal unitPrice;
        private BigDecimal discountPercent;
        private BigDecimal discountAmount;
        private CounterSaleLineKind lineKind;
        private String giftReason;
        private BigDecimal unitCostSnapshot;
        private BigDecimal totalCostSnapshot;
        private BigDecimal revenueTotal;
        private BigDecimal igvLine;
        private BigDecimal grossAfterDiscount;
        private List<Long> serialUnitIds;
    }

    private static class Totals {
        private final BigDecimal subtotal;
        private final BigDecimal discountTotal;
        private final BigDecimal igvAmount;
        private final BigDecimal total;
        private final BigDecimal giftCostTotal;

        private Totals(BigDecimal subtotal, BigDecimal discountTotal, BigDecimal igvAmount, BigDecimal total, BigDecimal giftCostTotal) {
            this.subtotal = subtotal;
            this.discountTotal = discountTotal;
            this.igvAmount = igvAmount;
            this.total = total;
            this.giftCostTotal = giftCostTotal;
        }
    }
}
