package com.paulfernandosr.possystembackend.salev2.application;

import com.paulfernandosr.possystembackend.salev2.domain.exception.InvalidSaleV2Exception;
import com.paulfernandosr.possystembackend.salev2.domain.model.*;
import com.paulfernandosr.possystembackend.salev2.domain.port.input.CreateSaleV2UseCase;
import com.paulfernandosr.possystembackend.salev2.domain.port.output.*;
import com.paulfernandosr.possystembackend.salev2.infrastructure.adapter.input.dto.SaleV2CreateRequest;
import com.paulfernandosr.possystembackend.salev2.infrastructure.adapter.input.dto.SaleV2DocumentResponse;
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
public class CreateSaleV2Service implements CreateSaleV2UseCase {

    private final UserRepository userRepository;

    private final DocumentSeriesRepository documentSeriesRepository;
    private final ProductSnapshotRepository productSnapshotRepository;

    private final SaleV2Repository saleV2Repository;
    private final SalePaymentRepository salePaymentRepository;

    private final ProductStockRepository productStockRepository;
    private final ProductStockMovementRepository productStockMovementRepository;

    private final ProductSerialUnitRepository productSerialUnitRepository;

    private final SaleSessionAccumulatorRepository saleSessionAccumulatorRepository;

    // Crédito / CxC
    private final AccountsReceivableRepository accountsReceivableRepository;
    private final CustomerAccountRepository customerAccountRepository;

    // Política de costo snapshot (Regla #8)
    private final CostPolicy costPolicy = CostPolicy.PROMEDIO;

    @Override
    @Transactional
    public SaleV2DocumentResponse create(SaleV2CreateRequest request, String username) {

        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new InvalidSaleV2Exception("Usuario inválido: " + username));

        if (user.isNotOnRegister()) {
            throw new InvalidSaleV2Exception("El usuario no tiene una sesión de caja abierta.");
        }

        validateRequest(request);

        LocalDate issueDate = request.getIssueDate() != null ? request.getIssueDate() : LocalDate.now();

        // 1) Reservar correlativo (FOR UPDATE) - Regla #2
        LockedDocumentSeries locked = documentSeriesRepository.lockSeries(
                request.getStationId(),
                request.getDocType().name(),
                request.getSeries()
        );

        Long number = locked.getNextNumber();

        // 2) Pre-cargar snapshots y validar reglas por ítem (incluye seriales) - Regla #3/#4/#6/#7
        List<ComputedLine> computedLines = new ArrayList<>();

        int lineNumber = 1;
        for (SaleV2CreateRequest.Item item : request.getItems()) {

            ProductSnapshot product = productSnapshotRepository.findSnapshotById(item.getProductId());
            if (product == null) {
                throw new InvalidSaleV2Exception("Producto no encontrado: " + item.getProductId());
            }

            BigDecimal quantity = nz(item.getQuantity());
            if (quantity.compareTo(BigDecimal.ZERO) <= 0) {
                throw new InvalidSaleV2Exception("Cantidad inválida para productId=" + item.getProductId());
            }

            LineKind kind = item.getLineKind() != null ? item.getLineKind() : LineKind.VENDIDO;

            // Obsequio: validaciones - Regla #7
            if (kind == LineKind.OBSEQUIO) {
                if (item.getGiftReason() == null || item.getGiftReason().trim().isEmpty()) {
                    throw new InvalidSaleV2Exception("giftReason es obligatorio para obsequios (productId=" + item.getProductId() + ")");
                }
                if (Boolean.FALSE.equals(product.getGiftAllowed())) {
                    throw new InvalidSaleV2Exception("El producto no permite obsequio: " + nzs(product.getSku()));
                }
            }

            // Seriales/VIN - Regla #6
            if (Boolean.TRUE.equals(product.getManageBySerial())) {
                // Cantidad debe ser entera
                int qtyInt;
                try {
                    qtyInt = quantity.intValueExact();
                } catch (ArithmeticException ex) {
                    throw new InvalidSaleV2Exception("Cantidad debe ser entera para productos con serie/VIN. productId=" + item.getProductId());
                }

                List<Long> serialIds = item.getSerialUnitIds();
                if (serialIds == null || serialIds.size() != qtyInt) {
                    throw new InvalidSaleV2Exception("Debe seleccionar exactamente " + qtyInt + " unidades (serialUnitIds) para productId=" + item.getProductId());
                }

                // Bloquear unidades y validar estado EN_ALMACEN
                List<SerialUnit> units = productSerialUnitRepository.lockByIds(serialIds);

                if (units.size() != serialIds.size()) {
                    throw new InvalidSaleV2Exception("Alguna(s) unidades serializadas no existen o no están disponibles.");
                }

                for (SerialUnit u : units) {
                    if (!Objects.equals(u.getProductId(), product.getId())) {
                        throw new InvalidSaleV2Exception("Unidad serializada no pertenece al producto. serialUnitId=" + u.getId());
                    }
                    if (!"EN_ALMACEN".equalsIgnoreCase(nzs(u.getStatus()))) {
                        throw new InvalidSaleV2Exception("Unidad serializada no disponible (status=" + u.getStatus() + "). serialUnitId=" + u.getId());
                    }
                }

                // Si es serializado, típicamente afecta stock; si tu negocio define lo contrario, ajusta.
                if (Boolean.FALSE.equals(product.getAffectsStock())) {
                    throw new InvalidSaleV2Exception("Producto serializado debe afectar stock. productId=" + product.getId());
                }

            } else {
                if (item.getSerialUnitIds() != null && !item.getSerialUnitIds().isEmpty()) {
                    throw new InvalidSaleV2Exception("serialUnitIds solo aplica para productos con manage_by_serial=true. productId=" + item.getProductId());
                }
            }

            // Precio por lista (snapshot) - Regla #3
            BigDecimal unitPrice = product.priceFor(request.getPriceList());
            if (unitPrice == null) unitPrice = BigDecimal.ZERO;

            if (kind == LineKind.OBSEQUIO) {
                unitPrice = BigDecimal.ZERO;
            }

            BigDecimal discountPercent = nz(item.getDiscountPercent());
            if (discountPercent.compareTo(BigDecimal.ZERO) < 0) {
                throw new InvalidSaleV2Exception("discountPercent no puede ser negativo. productId=" + item.getProductId());
            }

            BigDecimal gross = quantity.multiply(unitPrice);
            BigDecimal discountAmount = gross.multiply(discountPercent)
                    .divide(new BigDecimal("100"), 4, RoundingMode.HALF_UP);

            BigDecimal revenueTotal = (kind == LineKind.OBSEQUIO)
                    ? BigDecimal.ZERO
                    : gross.subtract(discountAmount);

            // Regla visible_in_document (Regla #4)
            boolean visibleInDocument = request.getDocType() == DocType.SIMPLE
                    || Boolean.TRUE.equals(product.getFacturableSunat());

            // Costo snapshot (Regla #8) si afecta stock
            BigDecimal unitCostSnapshot = null;
            BigDecimal totalCostSnapshot = null;
            boolean affectsStock = Boolean.TRUE.equals(product.getAffectsStock());
            if (affectsStock) {
                unitCostSnapshot = (costPolicy == CostPolicy.PROMEDIO)
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
                    .lineKind(kind)
                    .giftReason(item.getGiftReason())
                    .visibleInDocument(visibleInDocument)
                    .unitCostSnapshot(unitCostSnapshot)
                    .totalCostSnapshot(totalCostSnapshot)
                    .revenueTotal(revenueTotal.setScale(4, RoundingMode.HALF_UP))
                    .serialUnitIds(item.getSerialUnitIds())
                    .build());
        }

        // 3) Calcular totales cabecera (Regla #7/#8)
        Totals totals = calculateTotals(request, computedLines);

        // 3.1) Validaciones por documento (Regla #11)
        validateDocumentRules(request, totals);

        // 3.2) Preparar crédito (Regla #10)
        Integer creditDays = request.getCreditDays();
        LocalDate dueDate = request.getDueDate();

        if (request.getPaymentType() == PaymentType.CREDITO) {
            if (request.getCustomerId() == null) {
                throw new InvalidSaleV2Exception("customerId es obligatorio en CREDITO.");
            }

            if (dueDate == null) {
                if (creditDays == null || creditDays <= 0) {
                    throw new InvalidSaleV2Exception("creditDays debe ser > 0 cuando dueDate no se envía.");
                }
                dueDate = issueDate.plusDays(creditDays);
            } else {
                if (creditDays == null) {
                    creditDays = (int) java.time.temporal.ChronoUnit.DAYS.between(issueDate, dueDate);
                } else {
                    LocalDate expected = issueDate.plusDays(creditDays);
                    if (!expected.equals(dueDate)) {
                        throw new InvalidSaleV2Exception("dueDate no coincide con issueDate + creditDays.");
                    }
                }
            }

            // Validar elegibilidad de crédito contra customer_account (Regla #10)
            customerAccountRepository.ensureExists(request.getCustomerId());
            accountsReceivableRepository.markOverdueForCustomer(request.getCustomerId());
            customerAccountRepository.recalculate(request.getCustomerId());

            var account = customerAccountRepository.findByCustomerId(request.getCustomerId());
            if (account == null) {
                throw new InvalidSaleV2Exception("No se pudo obtener customer_account para customerId=" + request.getCustomerId());
            }
            if (!account.isCreditEnabled()) {
                throw new InvalidSaleV2Exception("Cliente bloqueado para crédito (credit_enabled=false).");
            }
            if (nz(account.getOverdueDebt()).compareTo(BigDecimal.ZERO) > 0) {
                throw new InvalidSaleV2Exception("Cliente con deuda vencida. No se permite crédito.");
            }
            if (nz(account.getCreditLimit()).compareTo(BigDecimal.ZERO) > 0) {
                BigDecimal projected = nz(account.getCurrentDebt()).add(totals.total);
                if (projected.compareTo(account.getCreditLimit()) > 0) {
                    throw new InvalidSaleV2Exception("Límite de crédito excedido. Límite=" + account.getCreditLimit() + ", deudaActual=" + account.getCurrentDebt() + ", venta=" + totals.total);
                }
            }

        } else {
            // CONTADO: no debería registrar campos de crédito
            creditDays = null;
            dueDate = null;
        }

        // 4) Insertar sale + items
        Long saleId = saleV2Repository.insertSale(
                request.getStationId(),
                request.getSaleSessionId(),
                user.getId(),
                request.getDocType().name(),
                request.getSeries(),
                number,
                issueDate,
                nzs(request.getCurrency(), "PEN"),
                request.getExchangeRate(),
                request.getPriceList().name(),
                request.getCustomerId(),
                request.getCustomerDocType(),
                request.getCustomerDocNumber(),
                request.getCustomerName(),
                request.getCustomerAddress(),
                request.getTaxStatus().name(),
                request.getTaxReason(),
                nz(request.getIgvRate(), new BigDecimal("18.00")),
                request.getPaymentType().name(),
                creditDays,
                dueDate,
                request.getNotes()
        );

        for (ComputedLine line : computedLines) {
            Long saleItemId = saleV2Repository.insertSaleItem(
                    saleId,
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
                    Boolean.TRUE.equals(line.getProduct().getFacturableSunat()),
                    Boolean.TRUE.equals(line.getProduct().getAffectsStock()),
                    line.getVisibleInDocument(),
                    line.getUnitCostSnapshot(),
                    line.getTotalCostSnapshot(),
                    line.getRevenueTotal()
            );

            // 5) Stock + Kardex (Regla #5)
            if (Boolean.TRUE.equals(line.getProduct().getAffectsStock())) {
                productStockRepository.decreaseOnHandOrFail(line.getProduct().getId(), line.getQuantity());
                productStockMovementRepository.createOutSale(line.getProduct().getId(), line.getQuantity(), saleItemId);
            }

            // 6) Seriales: asociar unidades a sale_item y marcar VENDIDO (Regla #6)
            if (Boolean.TRUE.equals(line.getProduct().getManageBySerial())) {
                for (Long serialUnitId : line.getSerialUnitIds()) {
                    productSerialUnitRepository.markAsSold(serialUnitId, saleItemId);
                }
            }
        }

        // 7) Actualizar totales
        saleV2Repository.updateTotals(
                saleId,
                totals.subtotal,
                totals.discountTotal,
                totals.igvAmount,
                totals.total,
                totals.giftCostTotal
        );

        // 8) Pago / CxC (Regla #9 y #10)
        Long arId = null;
        if (request.getPaymentType() == PaymentType.CONTADO) {
            if (request.getPayment() == null || request.getPayment().getMethod() == null) {
                throw new InvalidSaleV2Exception("payment.method es obligatorio en CONTADO.");
            }
            salePaymentRepository.insert(saleId, request.getPayment().getMethod().name(), totals.total);
        } else {
            // CREDITO: crear CxC y actualizar customer_account
            LocalDate finalDueDate = dueDate;
            String arStatus = (finalDueDate != null && finalDueDate.isBefore(LocalDate.now())) ? "VENCIDO" : "PENDIENTE";
            arId = accountsReceivableRepository.insert(
                    saleId,
                    request.getCustomerId(),
                    issueDate,
                    finalDueDate,
                    totals.total,
                    BigDecimal.ZERO,
                    totals.total,
                    arStatus
            );
            customerAccountRepository.touchLastSaleAt(request.getCustomerId());
            accountsReceivableRepository.markOverdueForCustomer(request.getCustomerId());
            customerAccountRepository.recalculate(request.getCustomerId());
        }

        // 9) Acumular en sesión (mantener sesión sin refactor)
        if (request.getSaleSessionId() != null) {
            saleSessionAccumulatorRepository.addSaleIncomeAndDiscount(request.getSaleSessionId(), totals.total, totals.discountTotal);
        }

        // 10) Incrementar correlativo (Regla #2)
        documentSeriesRepository.incrementNextNumber(locked.getId());

        return SaleV2DocumentResponse.builder()
                .saleId(saleId)
                .docType(request.getDocType())
                .series(request.getSeries())
                .number(number)
                .issueDate(issueDate)
                .subtotal(totals.subtotal)
                .discountTotal(totals.discountTotal)
                .igvAmount(totals.igvAmount)
                .total(totals.total)
                .giftCostTotal(totals.giftCostTotal)
                .paymentType(request.getPaymentType())
                .dueDate(dueDate)
                .accountsReceivableId(arId)
                .build();
    }

    private void validateDocumentRules(SaleV2CreateRequest request, Totals totals) {
        // FACTURA: exige RUC
        if (request.getDocType() == DocType.FACTURA) {
            if (request.getCustomerDocType() == null || !"RUC".equalsIgnoreCase(request.getCustomerDocType())) {
                throw new InvalidSaleV2Exception("FACTURA requiere customerDocType=RUC.");
            }
            if (request.getCustomerDocNumber() == null || request.getCustomerDocNumber().trim().isEmpty()) {
                throw new InvalidSaleV2Exception("FACTURA requiere customerDocNumber (RUC).");
            }
        }

        // Regla interna por monto: total >= 700 exige documento de identidad
        if (totals != null && totals.total != null && totals.total.compareTo(new BigDecimal("700")) >= 0) {
            if (request.getCustomerDocType() == null || request.getCustomerDocType().trim().isEmpty()
                    || request.getCustomerDocNumber() == null || request.getCustomerDocNumber().trim().isEmpty()) {
                throw new InvalidSaleV2Exception("Ventas >= 700 requieren documento (customerDocType y customerDocNumber)." );
            }
        }
    }

    private void validateRequest(SaleV2CreateRequest request) {
        if (request == null) throw new InvalidSaleV2Exception("Request vacío.");
        if (request.getStationId() == null) throw new InvalidSaleV2Exception("stationId es obligatorio.");
        if (request.getDocType() == null) throw new InvalidSaleV2Exception("docType es obligatorio.");
        if (request.getSeries() == null || request.getSeries().trim().isEmpty()) throw new InvalidSaleV2Exception("series es obligatorio.");
        if (request.getPriceList() == null) throw new InvalidSaleV2Exception("priceList es obligatorio.");
        if (request.getTaxStatus() == null) request.setTaxStatus(TaxStatus.NO_GRAVADA);
        if (request.getPaymentType() == null) throw new InvalidSaleV2Exception("paymentType es obligatorio.");
        if (request.getItems() == null || request.getItems().isEmpty()) throw new InvalidSaleV2Exception("Debe enviar items.");
    }

    private Totals calculateTotals(SaleV2CreateRequest request, List<ComputedLine> lines) {
        BigDecimal subtotal = BigDecimal.ZERO;
        BigDecimal discountTotal = BigDecimal.ZERO;
        BigDecimal giftCostTotal = BigDecimal.ZERO;

        for (ComputedLine l : lines) {
            subtotal = subtotal.add(l.getRevenueTotal());
            discountTotal = discountTotal.add(l.getDiscountAmount());

            if (l.getLineKind() == LineKind.OBSEQUIO && l.getTotalCostSnapshot() != null) {
                giftCostTotal = giftCostTotal.add(l.getTotalCostSnapshot());
            }
        }

        subtotal = subtotal.setScale(4, RoundingMode.HALF_UP);
        discountTotal = discountTotal.setScale(4, RoundingMode.HALF_UP);
        giftCostTotal = giftCostTotal.setScale(4, RoundingMode.HALF_UP);

        BigDecimal igvAmount = BigDecimal.ZERO;
        if (request.getTaxStatus() == TaxStatus.GRAVADA) {
            BigDecimal igvRate = nz(request.getIgvRate(), new BigDecimal("18.00"));
            igvAmount = subtotal.multiply(igvRate).divide(new BigDecimal("100"), 4, RoundingMode.HALF_UP);
        }

        BigDecimal total = subtotal.add(igvAmount).setScale(4, RoundingMode.HALF_UP);

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

    @lombok.Builder
    @lombok.Getter
    private static class ComputedLine {
        private Integer lineNumber;
        private ProductSnapshot product;
        private BigDecimal quantity;
        private BigDecimal unitPrice;
        private BigDecimal discountPercent;
        private BigDecimal discountAmount;
        private LineKind lineKind;
        private String giftReason;
        private Boolean visibleInDocument;
        private BigDecimal unitCostSnapshot;
        private BigDecimal totalCostSnapshot;
        private BigDecimal revenueTotal;
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
