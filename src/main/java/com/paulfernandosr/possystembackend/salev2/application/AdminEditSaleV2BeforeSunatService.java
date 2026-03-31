package com.paulfernandosr.possystembackend.salev2.application;

import com.paulfernandosr.possystembackend.salev2.domain.exception.InvalidSaleV2Exception;
import com.paulfernandosr.possystembackend.salev2.domain.model.*;
import com.paulfernandosr.possystembackend.salev2.domain.port.input.AdminEditSaleV2BeforeSunatUseCase;
import com.paulfernandosr.possystembackend.salev2.domain.port.output.*;
import com.paulfernandosr.possystembackend.salev2.infrastructure.adapter.input.dto.SaleV2AdminEditRequest;
import com.paulfernandosr.possystembackend.salev2.infrastructure.adapter.input.dto.SaleV2DocumentResponse;
import com.paulfernandosr.possystembackend.user.domain.User;
import com.paulfernandosr.possystembackend.user.domain.port.output.UserRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.HashMap;
import java.util.Map;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class AdminEditSaleV2BeforeSunatService implements AdminEditSaleV2BeforeSunatUseCase {

    private static final Set<String> SUNAT_EDITABLE_STATUSES = Set.of("", "NO_ENVIADO", "ERROR", "RECHAZADO", "NO_APLICA");

    private final UserRepository userRepository;
    private final ProductSnapshotRepository productSnapshotRepository;
    private final SaleV2Repository saleV2Repository;
    private final SalePaymentRepository salePaymentRepository;
    private final ProductStockRepository productStockRepository;
    private final ProductStockMovementRepository productStockMovementRepository;
    private final ProductSerialUnitRepository productSerialUnitRepository;
    private final AccountsReceivableRepository accountsReceivableRepository;
    private final AccountsReceivablePaymentRepository accountsReceivablePaymentRepository;
    private final CustomerAccountRepository customerAccountRepository;
    private final ObjectMapper objectMapper;

    private final CostPolicy costPolicy = CostPolicy.PROMEDIO;

    @Override
    @Transactional
    public SaleV2DocumentResponse edit(Long saleId, SaleV2AdminEditRequest request, String username) {
        if (saleId == null) {
            throw new InvalidSaleV2Exception("saleId es obligatorio.");
        }

        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new InvalidSaleV2Exception("Usuario inválido: " + username));

        validateRequest(request);

        SaleV2Repository.LockedEditableSale current = saleV2Repository.lockEditableById(saleId);
        if (current == null) {
            throw new InvalidSaleV2Exception("Venta no encontrada: " + saleId);
        }

        if (!"EMITIDA".equalsIgnoreCase(current.getStatus())) {
            throw new InvalidSaleV2Exception("Solo se puede editar una venta EMITIDA. Estado actual: " + current.getStatus());
        }

        if (isAlreadySentToSunat(current.getSunatStatus())) {
            throw new InvalidSaleV2Exception("La venta ya fue enviada/emita a SUNAT y ya no puede editarse. sunatStatus=" + current.getSunatStatus());
        }

        DocType docType = DocType.valueOf(current.getDocType());
        PaymentType paymentType = PaymentType.valueOf(current.getPaymentType());
        PriceList priceList = request.getPriceList() != null
                ? request.getPriceList()
                : PriceList.valueOf(current.getPriceList());

        TaxStatus taxStatus = request.getTaxStatus() != null
                ? request.getTaxStatus()
                : TaxStatus.valueOf(current.getTaxStatus());

        boolean igvIncluded = request.getIgvIncluded() != null
                ? Boolean.TRUE.equals(request.getIgvIncluded())
                : Boolean.TRUE.equals(current.getIgvIncluded());
        if (taxStatus != TaxStatus.GRAVADA) {
            igvIncluded = false;
        }

        BigDecimal igvRate = nz(request.getIgvRate(), nz(current.getIgvRate(), new BigDecimal("18.00")));
        LocalDate issueDate = request.getIssueDate() != null ? request.getIssueDate() : current.getIssueDate();
        String notes = request.getNotes() != null ? request.getNotes() : current.getNotes();

        Long customerId = request.getCustomerId() != null ? request.getCustomerId() : current.getCustomerId();
        String customerDocType = firstNonNull(request.getCustomerDocType(), current.getCustomerDocType());
        String customerDocNumber = firstNonNull(request.getCustomerDocNumber(), current.getCustomerDocNumber());
        String customerName = firstNonNull(request.getCustomerName(), current.getCustomerName());
        String customerAddress = firstNonNull(request.getCustomerAddress(), current.getCustomerAddress());
        String taxReason = request.getTaxReason() != null ? request.getTaxReason() : current.getTaxReason();
        if (taxStatus == TaxStatus.NO_GRAVADA && (taxReason == null || taxReason.trim().isEmpty())) {
            taxReason = "EXONERADA";
        }

        AccountsReceivableRepository.LockedAr currentAr = null;
        if (paymentType == PaymentType.CREDITO) {
            currentAr = accountsReceivableRepository.lockBySaleId(saleId);
            if (currentAr == null) {
                throw new InvalidSaleV2Exception("La venta a crédito no tiene cuenta por cobrar asociada.");
            }
            if (accountsReceivablePaymentRepository.existsByArId(currentAr.getId())
                    || nz(currentAr.getPaidAmount()).compareTo(BigDecimal.ZERO) > 0) {
                throw new InvalidSaleV2Exception("No se puede editar la venta porque ya tiene abonos registrados.");
            }
        }

        List<SaleV2Repository.SaleItemForVoid> previousItems = saleV2Repository.findItemsBySaleId(saleId);
        List<Long> previousItemIds = previousItems.stream().map(SaleV2Repository.SaleItemForVoid::getId).toList();

        var previousSerials = productSerialUnitRepository.lockBySaleItemIds(previousItemIds);
        String beforeSnapshotJson = toJson(buildBeforeSnapshot(current, previousItems, previousSerials));

        for (var serial : previousSerials) {
            productSerialUnitRepository.releaseFromSaleForEdition(serial.getId());
        }

        List<ComputedLine> computedLines = buildComputedLines(request, docType, priceList, taxStatus, igvIncluded, igvRate);
        Totals totals = calculateTotals(taxStatus, igvIncluded, igvRate, computedLines);
        validateDocumentRules(docType, customerDocType, customerDocNumber, totals, taxStatus, taxReason, computedLines);

        Integer creditDays = current.getCreditDays();
        LocalDate dueDate = current.getDueDate();

        if (paymentType == PaymentType.CREDITO) {
            if (customerId == null) {
                throw new InvalidSaleV2Exception("customerId es obligatorio en CREDITO.");
            }

            creditDays = request.getCreditDays() != null ? request.getCreditDays() : current.getCreditDays();
            dueDate = request.getDueDate() != null ? request.getDueDate() : current.getDueDate();

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

            customerAccountRepository.ensureExists(customerId);
            customerAccountRepository.recalculate(customerId);

            CustomerAccountSnapshot account = customerAccountRepository.findByCustomerId(customerId);
            if (account == null) {
                throw new InvalidSaleV2Exception("No se pudo obtener customer_account para customerId=" + customerId);
            }
            if (!account.isCreditEnabled()) {
                throw new InvalidSaleV2Exception("Cliente bloqueado para crédito (credit_enabled=false).");
            }
            if (accountsReceivableRepository.existsOpenOverdueDebtByCustomerExcludingSale(customerId, saleId)) {
                throw new InvalidSaleV2Exception("Cliente con deuda vencida (excluyendo la venta en edición). No se permite crédito.");
            }
            if (nz(account.getCreditLimit()).compareTo(BigDecimal.ZERO) > 0) {
                BigDecimal projected = nz(account.getCurrentDebt());
                if (Objects.equals(current.getCustomerId(), customerId) && currentAr != null) {
                    projected = projected.subtract(nz(currentAr.getBalanceAmount()));
                }
                projected = projected.add(totals.total);
                if (projected.compareTo(account.getCreditLimit()) > 0) {
                    throw new InvalidSaleV2Exception("Límite de crédito excedido. Límite="
                            + account.getCreditLimit() + ", deudaProyectada=" + projected);
                }
            }
        } else {
            creditDays = null;
            dueDate = null;
            if (request.getPayment() == null || request.getPayment().getMethod() == null) {
                throw new InvalidSaleV2Exception("payment.method es obligatorio para editar ventas CONTADO.");
            }
        }

        for (var item : previousItems) {
            if (Boolean.TRUE.equals(item.getAffectsStock())) {
                BigDecimal qty = nz(item.getQuantity());
                if (qty.signum() <= 0) continue;

                productStockRepository.increaseOnHand(item.getProductId(), qty);

                BigDecimal unitCost = nz(item.getUnitCostSnapshot());
                BigDecimal totalCost = item.getTotalCostSnapshot() != null
                        ? item.getTotalCostSnapshot()
                        : unitCost.multiply(qty);

                productStockMovementRepository.createInEdit(item.getProductId(), qty, item.getId(), unitCost, totalCost);
            }
        }

        saleV2Repository.deleteItemsBySaleId(saleId);

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

            if (Boolean.TRUE.equals(line.getProduct().getAffectsStock())) {
                productStockRepository.decreaseOnHandOrFail(line.getProduct().getId(), line.getQuantity());
                productStockMovementRepository.createOutSale(line.getProduct().getId(), line.getQuantity(), saleItemId);
            }

            if (Boolean.TRUE.equals(line.getProduct().getManageBySerial())) {
                for (Long serialUnitId : line.getSerialUnitIds()) {
                    productSerialUnitRepository.markAsSold(serialUnitId, saleItemId);
                }
            }
        }

        saleV2Repository.updateHeaderForAdminEdit(
                saleId,
                issueDate,
                priceList.name(),
                customerId,
                customerDocType,
                customerDocNumber,
                customerName,
                customerAddress,
                taxStatus.name(),
                taxReason,
                igvRate,
                igvIncluded,
                creditDays,
                dueDate,
                totals.subtotal,
                totals.discountTotal,
                totals.igvAmount,
                totals.total,
                totals.giftCostTotal,
                notes,
                user.getId(),
                request.getEditReason().trim()
        );

        String afterSnapshotJson = toJson(buildAfterSnapshot(
                current,
                issueDate,
                priceList,
                customerId,
                customerDocType,
                customerDocNumber,
                customerName,
                customerAddress,
                taxStatus,
                taxReason,
                igvRate,
                igvIncluded,
                paymentType,
                creditDays,
                dueDate,
                notes,
                totals,
                computedLines
        ));

        saleV2Repository.insertEditHistory(
                saleId,
                request.getEditReason().trim(),
                user.getId(),
                user.getUsername(),
                beforeSnapshotJson,
                afterSnapshotJson
        );

        Long arId = null;
        if (paymentType == PaymentType.CONTADO) {
            salePaymentRepository.deleteBySaleId(saleId);
            salePaymentRepository.insert(saleId, request.getPayment().getMethod().name(), totals.total);
        } else {
            String arStatus = (dueDate != null && dueDate.isBefore(LocalDate.now())) ? "VENCIDO" : "PENDIENTE";
            accountsReceivableRepository.updateBySaleId(
                    saleId,
                    customerId,
                    issueDate,
                    dueDate,
                    totals.total,
                    BigDecimal.ZERO,
                    totals.total,
                    arStatus
            );
            arId = currentAr.getId();
            customerAccountRepository.touchLastSaleAt(customerId);
            customerAccountRepository.recalculate(customerId);
        }

        if (current.getCustomerId() != null && !Objects.equals(current.getCustomerId(), customerId)) {
            customerAccountRepository.recalculate(current.getCustomerId());
        }

        return SaleV2DocumentResponse.builder()
                .saleId(saleId)
                .docType(docType)
                .series(current.getSeries())
                .number(current.getNumber())
                .issueDate(issueDate)
                .subtotal(totals.subtotal)
                .discountTotal(totals.discountTotal)
                .igvAmount(totals.igvAmount)
                .total(totals.total)
                .giftCostTotal(totals.giftCostTotal)
                .paymentType(paymentType)
                .dueDate(dueDate)
                .accountsReceivableId(arId)
                .build();
    }

    private List<ComputedLine> buildComputedLines(SaleV2AdminEditRequest request,
                                                  DocType docType,
                                                  PriceList priceList,
                                                  TaxStatus taxStatus,
                                                  boolean igvIncluded,
                                                  BigDecimal igvRate) {
        List<ComputedLine> computedLines = new ArrayList<>();
        int lineNumber = 1;

        for (SaleV2AdminEditRequest.Item item : request.getItems()) {
            ProductSnapshot product = productSnapshotRepository.findSnapshotById(item.getProductId());
            if (product == null) {
                throw new InvalidSaleV2Exception("Producto no encontrado: " + item.getProductId());
            }

            BigDecimal quantity = nz(item.getQuantity());
            if (quantity.compareTo(BigDecimal.ZERO) <= 0) {
                throw new InvalidSaleV2Exception("Cantidad inválida para productId=" + item.getProductId());
            }

            LineKind kind = item.getLineKind() != null ? item.getLineKind() : LineKind.VENDIDO;
            if (kind == LineKind.OBSEQUIO) {
                if (item.getGiftReason() == null || item.getGiftReason().trim().isEmpty()) {
                    throw new InvalidSaleV2Exception("giftReason es obligatorio para obsequios (productId=" + item.getProductId() + ")");
                }
                if (Boolean.FALSE.equals(product.getGiftAllowed())) {
                    throw new InvalidSaleV2Exception("El producto no permite obsequio: " + nzs(product.getSku()));
                }
            }

            if (Boolean.TRUE.equals(product.getManageBySerial())) {
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

                List<SerialUnit> units = productSerialUnitRepository.lockByIds(serialIds);
                if (units.size() != serialIds.size()) {
                    throw new InvalidSaleV2Exception("Alguna(s) unidades serializadas no existen o no están disponibles.");
                }

                for (SerialUnit unit : units) {
                    if (!Objects.equals(unit.getProductId(), product.getId())) {
                        throw new InvalidSaleV2Exception("Unidad serializada no pertenece al producto. serialUnitId=" + unit.getId());
                    }
                    String status = nzs(unit.getStatus()).toUpperCase();
                    if ("EN_ALMACEN".equals(status)) {
                        continue;
                    }
                    if ("RESERVADO".equals(status)) {
                        if (unit.getContractId() == null || unit.getVin() == null || unit.getVin().trim().isEmpty()) {
                            throw new InvalidSaleV2Exception("Unidad RESERVADO no disponible (sin contrato/VIN). serialUnitId=" + unit.getId());
                        }
                        continue;
                    }
                    throw new InvalidSaleV2Exception("Unidad serializada no disponible (status=" + unit.getStatus() + "). serialUnitId=" + unit.getId());
                }

                if (Boolean.FALSE.equals(product.getAffectsStock())) {
                    throw new InvalidSaleV2Exception("Producto serializado debe afectar stock. productId=" + product.getId());
                }
            } else if (item.getSerialUnitIds() != null && !item.getSerialUnitIds().isEmpty()) {
                throw new InvalidSaleV2Exception("serialUnitIds solo aplica para productos con manage_by_serial=true. productId=" + item.getProductId());
            }

            BigDecimal unitPrice = item.getUnitPriceOverride() != null
                    ? item.getUnitPriceOverride()
                    : product.priceFor(priceList);
            if (unitPrice == null) unitPrice = BigDecimal.ZERO;
            if (kind == LineKind.OBSEQUIO) {
                unitPrice = BigDecimal.ZERO;
            }

            BigDecimal discountPercent = nz(item.getDiscountPercent());
            if (discountPercent.compareTo(BigDecimal.ZERO) < 0) {
                throw new InvalidSaleV2Exception("discountPercent no puede ser negativo. productId=" + item.getProductId());
            }
            if (docType != DocType.SIMPLE && discountPercent.compareTo(BigDecimal.ZERO) != 0) {
                throw new InvalidSaleV2Exception("Descuentos solo permitidos para ventas SIMPLE. En BOLETA/FACTURA el descuento debe ser 0. productId=" + item.getProductId());
            }

            BigDecimal gross = quantity.multiply(unitPrice);
            BigDecimal discountAmount = (kind == LineKind.OBSEQUIO)
                    ? BigDecimal.ZERO
                    : gross.multiply(discountPercent)
                    .divide(new BigDecimal("100"), 4, RoundingMode.HALF_UP);

            BigDecimal grossAfterDiscount = (kind == LineKind.OBSEQUIO)
                    ? BigDecimal.ZERO
                    : gross.subtract(discountAmount).setScale(4, RoundingMode.HALF_UP);

            BigDecimal baseLine = grossAfterDiscount;
            BigDecimal igvLine = BigDecimal.ZERO;
            if (taxStatus == TaxStatus.GRAVADA && igvIncluded && kind != LineKind.OBSEQUIO) {
                BigDecimal divisor = BigDecimal.ONE.add(
                        igvRate.divide(new BigDecimal("100"), 10, RoundingMode.HALF_UP)
                );
                baseLine = grossAfterDiscount.divide(divisor, 4, RoundingMode.HALF_UP);
                igvLine = grossAfterDiscount.subtract(baseLine).setScale(4, RoundingMode.HALF_UP);
            }

            boolean visibleInDocument = docType == DocType.SIMPLE || Boolean.TRUE.equals(product.getFacturableSunat());

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
                    .revenueTotal(baseLine.setScale(4, RoundingMode.HALF_UP))
                    .igvLine(igvLine.setScale(4, RoundingMode.HALF_UP))
                    .grossAfterDiscount(grossAfterDiscount)
                    .serialUnitIds(item.getSerialUnitIds())
                    .build());
        }

        return computedLines;
    }

    private void validateDocumentRules(DocType docType,
                                       String customerDocType,
                                       String customerDocNumber,
                                       Totals totals,
                                       TaxStatus taxStatus,
                                       String taxReason,
                                       List<ComputedLine> lines) {
        if (docType == DocType.FACTURA) {
            if (customerDocType == null || !"RUC".equalsIgnoreCase(customerDocType)) {
                throw new InvalidSaleV2Exception("FACTURA requiere customerDocType=RUC.");
            }
            if (customerDocNumber == null || customerDocNumber.trim().isEmpty()) {
                throw new InvalidSaleV2Exception("FACTURA requiere customerDocNumber (RUC).");
            }
        }

        if (totals != null && totals.total != null && totals.total.compareTo(new BigDecimal("700")) >= 0) {
            if (customerDocType == null || customerDocType.trim().isEmpty()
                    || customerDocNumber == null || customerDocNumber.trim().isEmpty()) {
                throw new InvalidSaleV2Exception("Ventas >= 700 requieren documento (customerDocType y customerDocNumber).");
            }
        }

        if (docType == DocType.BOLETA || docType == DocType.FACTURA) {
            if (taxStatus == TaxStatus.NO_GRAVADA
                    && (taxReason == null || taxReason.trim().isEmpty())) {
                throw new InvalidSaleV2Exception("BOLETA/FACTURA NO_GRAVADA requieren taxReason. En tu flujo debe ser EXONERADA.");
            }

            if (customerDocType == null || customerDocType.trim().isEmpty()
                    || customerDocNumber == null || customerDocNumber.trim().isEmpty()) {
                throw new InvalidSaleV2Exception("BOLETA/FACTURA requieren customerDocType y customerDocNumber.");
            }

            lines.stream()
                    .filter(line -> !Boolean.TRUE.equals(line.getVisibleInDocument()))
                    .findAny()
                    .ifPresent(line -> {
                        throw new InvalidSaleV2Exception("BOLETA/FACTURA no permiten líneas ocultas para SUNAT. productId="
                                + line.getProduct().getId());
                    });

            lines.stream()
                    .filter(line -> line.getLineKind() != LineKind.VENDIDO)
                    .findAny()
                    .ifPresent(line -> {
                        throw new InvalidSaleV2Exception("BOLETA/FACTURA solo permiten lineKind=VENDIDO en el flujo de emisión SUNAT desacoplada actual. productId="
                                + line.getProduct().getId());
                    });
        }
    }

    private void validateRequest(SaleV2AdminEditRequest request) {
        if (request == null) throw new InvalidSaleV2Exception("Request vacío.");
        if (request.getItems() == null || request.getItems().isEmpty()) {
            throw new InvalidSaleV2Exception("Debe enviar items.");
        }
        if (request.getEditReason() == null || request.getEditReason().trim().isEmpty()) {
            throw new InvalidSaleV2Exception("editReason es obligatorio.");
        }
        if (request.getEditReason().length() > 500) {
            throw new InvalidSaleV2Exception("editReason no puede exceder 500 caracteres.");
        }
    }

    private Totals calculateTotals(TaxStatus taxStatus,
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

            if (line.getLineKind() == LineKind.OBSEQUIO && line.getTotalCostSnapshot() != null) {
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

        if (taxStatus == TaxStatus.GRAVADA && !igvIncluded) {
            igvAmount = subtotal.multiply(igvRate).divide(new BigDecimal("100"), 4, RoundingMode.HALF_UP);
            total = subtotal.add(igvAmount).setScale(4, RoundingMode.HALF_UP);
        } else if (taxStatus != TaxStatus.GRAVADA) {
            igvAmount = BigDecimal.ZERO;
            total = subtotal.setScale(4, RoundingMode.HALF_UP);
        } else {
            igvAmount = igvAmount.setScale(4, RoundingMode.HALF_UP);
            total = total.setScale(4, RoundingMode.HALF_UP);
        }

        return new Totals(subtotal, discountTotal, igvAmount, total, giftCostTotal);
    }

    private boolean isAlreadySentToSunat(String sunatStatus) {
        String normalized = nzs(sunatStatus).trim().toUpperCase();
        return !SUNAT_EDITABLE_STATUSES.contains(normalized);
    }

    private static BigDecimal nz(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private static BigDecimal nz(BigDecimal value, BigDecimal def) {
        return value == null ? def : value;
    }

    private static String nzs(String value) {
        return value == null ? "" : value;
    }

    private static <T> T firstNonNull(T value, T fallback) {
        return value != null ? value : fallback;
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
        private BigDecimal igvLine;
        private BigDecimal grossAfterDiscount;
        private List<Long> serialUnitIds;
    }


    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new InvalidSaleV2Exception("No se pudo serializar el historial de edición.");
        }
    }

    private Map<String, Object> buildBeforeSnapshot(SaleV2Repository.LockedEditableSale current,
                                                    List<SaleV2Repository.SaleItemForVoid> previousItems,
                                                    List<SerialUnit> previousSerials) {
        List<Map<String, Object>> items = new ArrayList<>();
        Map<Long, List<Map<String, Object>>> serialsByProductId = buildSerialsByProductId(previousSerials);

        for (SaleV2Repository.SaleItemForVoid item : previousItems) {
            Map<String, Object> itemMap = new LinkedHashMap<>();
            itemMap.put("saleItemId", item.getId());
            itemMap.put("lineNumber", item.getLineNumber());
            itemMap.put("productId", item.getProductId());
            itemMap.put("sku", item.getSku());
            itemMap.put("description", item.getDescription());
            itemMap.put("presentation", item.getPresentation());
            itemMap.put("factor", item.getFactor());
            itemMap.put("quantity", item.getQuantity());
            itemMap.put("unitPrice", item.getUnitPrice());
            itemMap.put("discountPercent", item.getDiscountPercent());
            itemMap.put("discountAmount", item.getDiscountAmount());
            itemMap.put("lineKind", item.getLineKind());
            itemMap.put("giftReason", item.getGiftReason());
            itemMap.put("facturableSunat", item.getFacturableSunat());
            itemMap.put("affectsStock", item.getAffectsStock());
            itemMap.put("visibleInDocument", item.getVisibleInDocument());
            itemMap.put("revenueTotal", item.getRevenueTotal());
            itemMap.put("unitCostSnapshot", item.getUnitCostSnapshot());
            itemMap.put("totalCostSnapshot", item.getTotalCostSnapshot());
            itemMap.put("serialUnits", serialsByProductId.getOrDefault(item.getProductId(), List.of()));
            items.add(itemMap);
        }

        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("saleId", current.getId());
        snapshot.put("docType", current.getDocType());
        snapshot.put("series", current.getSeries());
        snapshot.put("number", current.getNumber());
        snapshot.put("issueDate", current.getIssueDate());
        snapshot.put("currency", current.getCurrency());
        snapshot.put("exchangeRate", current.getExchangeRate());
        snapshot.put("priceList", current.getPriceList());
        snapshot.put("customerId", current.getCustomerId());
        snapshot.put("customerDocType", current.getCustomerDocType());
        snapshot.put("customerDocNumber", current.getCustomerDocNumber());
        snapshot.put("customerName", current.getCustomerName());
        snapshot.put("customerAddress", current.getCustomerAddress());
        snapshot.put("taxStatus", current.getTaxStatus());
        snapshot.put("taxReason", current.getTaxReason());
        snapshot.put("igvRate", current.getIgvRate());
        snapshot.put("igvIncluded", current.getIgvIncluded());
        snapshot.put("paymentType", current.getPaymentType());
        snapshot.put("creditDays", current.getCreditDays());
        snapshot.put("dueDate", current.getDueDate());
        snapshot.put("notes", current.getNotes());
        snapshot.put("saleStatus", current.getStatus());
        snapshot.put("sunatStatus", current.getSunatStatus());
        snapshot.put("editStatus", current.getEditStatus());
        snapshot.put("editCount", current.getEditCount());
        snapshot.put("lastEditedAt", current.getLastEditedAt());
        snapshot.put("lastEditedBy", current.getLastEditedBy());
        snapshot.put("lastEditReason", current.getLastEditReason());
        snapshot.put("total", current.getTotal());
        snapshot.put("discountTotal", current.getDiscountTotal());
        snapshot.put("items", items);
        return snapshot;
    }

    private Map<String, Object> buildAfterSnapshot(SaleV2Repository.LockedEditableSale current,
                                                   LocalDate issueDate,
                                                   PriceList priceList,
                                                   Long customerId,
                                                   String customerDocType,
                                                   String customerDocNumber,
                                                   String customerName,
                                                   String customerAddress,
                                                   TaxStatus taxStatus,
                                                   String taxReason,
                                                   BigDecimal igvRate,
                                                   boolean igvIncluded,
                                                   PaymentType paymentType,
                                                   Integer creditDays,
                                                   LocalDate dueDate,
                                                   String notes,
                                                   Totals totals,
                                                   List<ComputedLine> computedLines) {
        List<Map<String, Object>> items = new ArrayList<>();
        for (ComputedLine line : computedLines) {
            Map<String, Object> itemMap = new LinkedHashMap<>();
            itemMap.put("lineNumber", line.getLineNumber());
            itemMap.put("productId", line.getProduct().getId());
            itemMap.put("sku", line.getProduct().getSku());
            itemMap.put("description", line.getProduct().getName());
            itemMap.put("presentation", line.getProduct().getPresentation());
            itemMap.put("factor", line.getProduct().getFactor());
            itemMap.put("quantity", line.getQuantity());
            itemMap.put("unitPrice", line.getUnitPrice());
            itemMap.put("discountPercent", line.getDiscountPercent());
            itemMap.put("discountAmount", line.getDiscountAmount());
            itemMap.put("lineKind", line.getLineKind().name());
            itemMap.put("giftReason", line.getGiftReason());
            itemMap.put("facturableSunat", line.getProduct().getFacturableSunat());
            itemMap.put("affectsStock", line.getProduct().getAffectsStock());
            itemMap.put("visibleInDocument", line.getVisibleInDocument());
            itemMap.put("revenueTotal", line.getRevenueTotal());
            itemMap.put("igvLine", line.getIgvLine());
            itemMap.put("grossAfterDiscount", line.getGrossAfterDiscount());
            itemMap.put("unitCostSnapshot", line.getUnitCostSnapshot());
            itemMap.put("totalCostSnapshot", line.getTotalCostSnapshot());
            itemMap.put("serialUnitIds", line.getSerialUnitIds());
            items.add(itemMap);
        }

        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("saleId", current.getId());
        snapshot.put("docType", current.getDocType());
        snapshot.put("series", current.getSeries());
        snapshot.put("number", current.getNumber());
        snapshot.put("issueDate", issueDate);
        snapshot.put("currency", current.getCurrency());
        snapshot.put("exchangeRate", current.getExchangeRate());
        snapshot.put("priceList", priceList.name());
        snapshot.put("customerId", customerId);
        snapshot.put("customerDocType", customerDocType);
        snapshot.put("customerDocNumber", customerDocNumber);
        snapshot.put("customerName", customerName);
        snapshot.put("customerAddress", customerAddress);
        snapshot.put("taxStatus", taxStatus.name());
        snapshot.put("taxReason", taxReason);
        snapshot.put("igvRate", igvRate);
        snapshot.put("igvIncluded", igvIncluded);
        snapshot.put("paymentType", paymentType.name());
        snapshot.put("creditDays", creditDays);
        snapshot.put("dueDate", dueDate);
        snapshot.put("notes", notes);
        snapshot.put("saleStatus", current.getStatus());
        snapshot.put("sunatStatus", "SIMPLE".equalsIgnoreCase(current.getDocType()) ? "NO_APLICA" : "NO_ENVIADO");
        snapshot.put("editStatus", "EDITADA");
        snapshot.put("total", totals.total);
        snapshot.put("subtotal", totals.subtotal);
        snapshot.put("discountTotal", totals.discountTotal);
        snapshot.put("igvAmount", totals.igvAmount);
        snapshot.put("giftCostTotal", totals.giftCostTotal);
        snapshot.put("items", items);
        return snapshot;
    }

    private Map<Long, List<Map<String, Object>>> buildSerialsByProductId(List<SerialUnit> serialUnits) {
        Map<Long, List<Map<String, Object>>> result = new HashMap<>();
        for (SerialUnit serial : serialUnits) {
            Map<String, Object> serialMap = new LinkedHashMap<>();
            serialMap.put("id", serial.getId());
            serialMap.put("productId", serial.getProductId());
            serialMap.put("status", serial.getStatus());
            serialMap.put("vin", serial.getVin());
            serialMap.put("contractId", serial.getContractId());

            result.computeIfAbsent(serial.getProductId(), key -> new ArrayList<>()).add(serialMap);
        }
        return result;
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
