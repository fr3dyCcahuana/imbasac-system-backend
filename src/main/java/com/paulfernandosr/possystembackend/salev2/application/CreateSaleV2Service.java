package com.paulfernandosr.possystembackend.salev2.application;

import com.paulfernandosr.possystembackend.common.infrastructure.documentseries.DocumentSeriesPolicy;
import com.paulfernandosr.possystembackend.proformav2.domain.Proforma;
import com.paulfernandosr.possystembackend.proformav2.domain.ProformaItem;
import com.paulfernandosr.possystembackend.proformav2.domain.model.ProformaStatus;
import com.paulfernandosr.possystembackend.proformav2.domain.port.output.ProformaRepository;
import com.paulfernandosr.possystembackend.proformav2.domain.port.output.ProformaItemRepository;
import com.paulfernandosr.possystembackend.proformav2.domain.port.output.SaleReferenceRepository;
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

    private static final BigDecimal GENERIC_CUSTOMER_TOTAL_LIMIT = new BigDecimal("700.00");

    private final UserRepository userRepository;

    private final DocumentSeriesRepository documentSeriesRepository;
    private final ProductSnapshotRepository productSnapshotRepository;
    private final DocumentSeriesPolicy documentSeriesPolicy;

    private final SaleV2Repository saleV2Repository;
    private final CustomerLocationSnapshotRepository customerLocationSnapshotRepository;
    private final SalePaymentRepository salePaymentRepository;

    private final ProductStockRepository productStockRepository;
    private final ProductStockMovementRepository productStockMovementRepository;

    private final ProductSerialUnitRepository productSerialUnitRepository;

    private final SaleSessionControlRepository saleSessionControlRepository;
    private final SaleSessionAccumulatorRepository saleSessionAccumulatorRepository;

    // Crédito / CxC
    private final AccountsReceivableRepository accountsReceivableRepository;
    private final CustomerAccountRepository customerAccountRepository;

    // Proforma origen (Opción B): POST /sales/v2 con número visible de proforma
    private final ProformaRepository proformaRepository;
    private final ProformaItemRepository proformaItemRepository;
    private final SaleReferenceRepository saleReferenceRepository;

    // Política de costo snapshot (Regla #8)
    private final CostPolicy costPolicy = CostPolicy.PROMEDIO;

    @Override
    @Transactional
    public SaleV2DocumentResponse create(SaleV2CreateRequest request, String username) {

        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new InvalidSaleV2Exception("Usuario inválido: " + username));

        validateRequest(request);

        // Si viene de proforma, bloquearla desde el inicio de la transacción
        // para evitar doble conversión concurrente.
        // IMPORTANTE: ahora el frontend envía sourceProformaNumber (número visible),
        // y el backend resuelve el ID interno real para guardar la relación.
        Proforma sourceProforma = lockAndValidateSourceProforma(request);

        List<ProformaItem> sourceProformaItems = List.of();
        if (sourceProforma != null) {
            sourceProformaItems = proformaItemRepository.findByProformaId(sourceProforma.getId());
            request.setItems(filterFacturableItemsForSaleFromProforma(request.getItems(), sourceProformaItems));
        }

        Long resolvedSaleSessionId = null;

        if (user.requiresCashSession()) {
            /*
             * stationId opcional:
             * - Si viene stationId, se valida contra la sesión abierta.
             * - Si NO viene stationId, se permite continuar sin caja/estación.
             *   Esto aplica para ventas generadas desde contrato.
             */
            if (request.getStationId() != null || request.getSaleSessionId() != null) {
                if (user.isNotOnRegister()) {
                    throw new InvalidSaleV2Exception("El usuario no tiene una sesión de caja abierta.");
                }

                OpenSaleSession openSession = saleSessionControlRepository.findOpenByUserId(user.getId());
                if (openSession == null) {
                    throw new InvalidSaleV2Exception("El usuario no tiene una sesión de caja abierta.");
                }

                if (request.getStationId() != null
                        && !Objects.equals(openSession.getStationId(), request.getStationId())) {
                    throw new InvalidSaleV2Exception("La estación de la venta no coincide con la sesión de caja abierta del usuario.");
                }

                if (request.getSaleSessionId() != null
                        && !Objects.equals(request.getSaleSessionId(), openSession.getId())) {
                    throw new InvalidSaleV2Exception("saleSessionId no coincide con la sesión de caja abierta del usuario.");
                }

                resolvedSaleSessionId = openSession.getId();
            }
        } else if (request.getSaleSessionId() != null) {
            throw new InvalidSaleV2Exception("Este rol no debe enviar saleSessionId porque no trabaja con apertura de caja.");
        }

        LocalDate issueDate = request.getIssueDate() != null ? request.getIssueDate() : LocalDate.now();

        boolean igvIncluded = Boolean.TRUE.equals(request.getIgvIncluded());
        if (request.getTaxStatus() != TaxStatus.GRAVADA) {
            igvIncluded = false;
        }

        BigDecimal igvRate = nz(request.getIgvRate(), new BigDecimal("18.00"));

        // 1) Reservar correlativo (FOR UPDATE) - Regla #2
        LockedDocumentSeries locked = documentSeriesRepository.lockSeries(
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

                for (SerialUnit u : units) {
                    if (!Objects.equals(u.getProductId(), product.getId())) {
                        throw new InvalidSaleV2Exception("Unidad serializada no pertenece al producto. serialUnitId=" + u.getId());
                    }
                    String st = nzs(u.getStatus()).toUpperCase();
                    if ("EN_ALMACEN".equals(st)) {
                        // ok
                    } else if ("RESERVADO".equals(st)) {
                        // ✅ permitido solo para MOTOCICLETAS reservadas por contrato
                        if (u.getContractId() == null || u.getVin() == null || u.getVin().trim().isEmpty()) {
                            throw new InvalidSaleV2Exception("Unidad RESERVADO no disponible (sin contrato/VIN). serialUnitId=" + u.getId());
                        }
                    } else {
                        throw new InvalidSaleV2Exception("Unidad serializada no disponible (status=" + u.getStatus() + "). serialUnitId=" + u.getId());
                    }
                }

                if (Boolean.FALSE.equals(product.getAffectsStock())) {
                    throw new InvalidSaleV2Exception("Producto serializado debe afectar stock. productId=" + product.getId());
                }

            } else {
                if (item.getSerialUnitIds() != null && !item.getSerialUnitIds().isEmpty()) {
                    throw new InvalidSaleV2Exception("serialUnitIds solo aplica para productos con manage_by_serial=true. productId=" + item.getProductId());
                }
            }

            // Precio por lista (snapshot) con override
            BigDecimal unitPrice = (item.getUnitPriceOverride() != null)
                    ? item.getUnitPriceOverride()
                    : product.priceFor(request.getPriceList());

            if (unitPrice == null) unitPrice = BigDecimal.ZERO;

            if (kind == LineKind.OBSEQUIO) {
                unitPrice = BigDecimal.ZERO;
            }

            // Descuento: SOLO SIMPLE (regla de negocio)
            BigDecimal discountPercent = nz(item.getDiscountPercent());
            if (discountPercent.compareTo(BigDecimal.ZERO) < 0) {
                throw new InvalidSaleV2Exception("discountPercent no puede ser negativo. productId=" + item.getProductId());
            }
            if (request.getDocType() != DocType.SIMPLE && discountPercent.compareTo(BigDecimal.ZERO) != 0) {
                throw new InvalidSaleV2Exception("Descuentos solo permitidos para ventas SIMPLE. En BOLETA/FACTURA el descuento debe ser 0. productId=" + item.getProductId());
            }

            // Cálculo base por línea:
            BigDecimal gross = quantity.multiply(unitPrice);

            // Si igvIncluded=true y taxStatus=GRAVADA, el descuento se aplica sobre el precio final (gross incluye IGV)
            BigDecimal discountAmount = (kind == LineKind.OBSEQUIO)
                    ? BigDecimal.ZERO
                    : gross.multiply(discountPercent)
                    .divide(new BigDecimal("100"), 4, RoundingMode.HALF_UP);

            BigDecimal grossAfterDiscount = (kind == LineKind.OBSEQUIO)
                    ? BigDecimal.ZERO
                    : gross.subtract(discountAmount).setScale(4, RoundingMode.HALF_UP);

            // Base/IGV por línea (solo necesario si igvIncluded=true)
            BigDecimal baseLine = grossAfterDiscount;
            BigDecimal igvLine = BigDecimal.ZERO;

            if (request.getTaxStatus() == TaxStatus.GRAVADA && igvIncluded && kind != LineKind.OBSEQUIO) {
                BigDecimal divisor = BigDecimal.ONE.add(
                        igvRate.divide(new BigDecimal("100"), 10, RoundingMode.HALF_UP)
                ); // 1.18

                baseLine = grossAfterDiscount.divide(divisor, 4, RoundingMode.HALF_UP);
                igvLine = grossAfterDiscount.subtract(baseLine).setScale(4, RoundingMode.HALF_UP);
            }

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
                    // revenueTotal siempre = BASE (subtotal)
                    .revenueTotal(baseLine.setScale(4, RoundingMode.HALF_UP))
                    // solo se usa si igvIncluded=true
                    .igvLine(igvLine.setScale(4, RoundingMode.HALF_UP))
                    .grossAfterDiscount(grossAfterDiscount) // total final por línea si igvIncluded=true
                    .serialUnitIds(item.getSerialUnitIds())
                    .build());
        }

        // 3) Calcular totales cabecera
        Totals totals = calculateTotals(request, computedLines);

        // 3.1) Validaciones por documento (Regla #11)
        validateDocumentRules(request, totals, computedLines);

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

            customerAccountRepository.ensureExists(request.getCustomerId());
            customerAccountRepository.recalculate(request.getCustomerId());

        } else {
            creditDays = null;
            dueDate = null;
        }

        boolean finalIgvIncluded = Boolean.TRUE.equals(request.getIgvIncluded()) && request.getTaxStatus() == TaxStatus.GRAVADA;
        CustomerLocationSnapshot customerLocation = resolveCustomerLocation(request);

        // 4) Insertar sale + items
        Long saleId = saleV2Repository.insertSale(
                request.getStationId(),
                resolvedSaleSessionId,
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
                customerLocation.getUbigeo(),
                customerLocation.getDepartment(),
                customerLocation.getProvince(),
                customerLocation.getDistrict(),
                request.getTaxStatus().name(),
                request.getTaxReason(),
                nz(request.getIgvRate(), new BigDecimal("18.00")),
                finalIgvIncluded,
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

            if (Boolean.TRUE.equals(line.getProduct().getAffectsStock())) {
                StockMovementBalance balance = productStockRepository.decreaseOnHandOrFail(line.getProduct().getId(), line.getQuantity());
                BigDecimal unitCost = nz(line.getUnitCostSnapshot());
                BigDecimal totalCost = line.getTotalCostSnapshot() != null
                        ? line.getTotalCostSnapshot()
                        : unitCost.multiply(line.getQuantity()).setScale(4, RoundingMode.HALF_UP);
                productStockMovementRepository.createOutSale(
                        line.getProduct().getId(),
                        line.getQuantity(),
                        saleItemId,
                        unitCost,
                        totalCost,
                        balance.getQuantityOnHand(),
                        nz(balance.getAverageCost(), unitCost)
                );
            }

            if (Boolean.TRUE.equals(line.getProduct().getManageBySerial())) {
                for (Long serialUnitId : line.getSerialUnitIds()) {
                    productSerialUnitRepository.markAsSold(serialUnitId, saleItemId);
                }
            }
        }

        processInternalProformaStockMovements(sourceProformaItems);

        // 7) Actualizar totales
        saleV2Repository.updateTotals(
                saleId,
                totals.subtotal,
                totals.discountTotal,
                totals.igvAmount,
                totals.total,
                totals.giftCostTotal
        );

        // 8) Pago / CxC
        Long arId = null;
        if (request.getPaymentType() == PaymentType.CONTADO) {
            if (request.getPayment() == null || request.getPayment().getMethod() == null) {
                throw new InvalidSaleV2Exception("payment.method es obligatorio en CONTADO.");
            }
            salePaymentRepository.insert(saleId, request.getPayment().getMethod().name(), totals.total);
        } else {
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
            customerAccountRepository.recalculate(request.getCustomerId());
        }

        if (request.getPaymentType() == PaymentType.CONTADO && resolvedSaleSessionId != null) {
            saleSessionAccumulatorRepository.addSaleIncomeAndDiscount(
                    resolvedSaleSessionId,
                    totals.total,
                    totals.discountTotal
            );
        }

        markSourceProformaAsConverted(sourceProforma, saleId, user.getId());

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

    private CustomerLocationSnapshot resolveCustomerLocation(SaleV2CreateRequest request) {
        if (request == null || isGenericCustomerDocumentType(request.getCustomerDocType())) {
            return new CustomerLocationSnapshot();
        }

        CustomerLocationSnapshot fromDb = customerLocationSnapshotRepository.resolveCustomerLocation(
                        request.getCustomerId(),
                        request.getCustomerDocType(),
                        request.getCustomerDocNumber(),
                        request.getCustomerAddress()
                )
                .orElseGet(CustomerLocationSnapshot::new);

        return CustomerLocationSnapshot.builder()
                .ubigeo(firstText(request.getCustomerUbigeo(), fromDb.getUbigeo()))
                .department(firstText(request.getCustomerDepartment(), fromDb.getDepartment()))
                .province(firstText(request.getCustomerProvince(), fromDb.getProvince()))
                .district(firstText(request.getCustomerDistrict(), fromDb.getDistrict()))
                .build();
    }

    private String firstText(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return null;
    }

    private Proforma lockAndValidateSourceProforma(SaleV2CreateRequest request) {
        Long requestedProformaNumber = request.getSourceProformaNumber();

        /*
         * Compatibilidad temporal:
         * - Nuevo frontend: envía sourceProformaNumber = número visible de la proforma.
         * - Frontend anterior: todavía puede enviar sourceProformaId, pero ese valor venía
         *   desde el campo visual del usuario. Para evitar volver a convertir el número visible
         *   como ID interno, se interpreta como NÚMERO visible, nunca como proforma.id.
         *
         * Regla definitiva:
         * 1) Resolver siempre la proforma por p.number.
         * 2) Usar recién proforma.getId() para guardar sale.source_proforma_id,
         *    sale_reference.proforma_id y proforma.converted_sale_id.
         */
        if (requestedProformaNumber == null) {
            requestedProformaNumber = request.getSourceProformaId();
        }

        if (requestedProformaNumber == null) {
            return null;
        }

        if (requestedProformaNumber <= 0) {
            throw new InvalidSaleV2Exception("Número de proforma origen inválido: " + requestedProformaNumber);
        }

        final Long proformaNumber = requestedProformaNumber;

        Proforma proforma = proformaRepository.lockByNumber(proformaNumber)
                .orElseThrow(() -> new InvalidSaleV2Exception(
                        "Proforma origen no encontrada con número: " + proformaNumber
                ));

        if (proforma.getStatus() != ProformaStatus.PENDIENTE) {
            throw new InvalidSaleV2Exception("La proforma Nro " + proformaNumber
                    + " no está PENDIENTE. Estado actual: " + proforma.getStatus());
        }

        return proforma;
    }

    private void markSourceProformaAsConverted(Proforma sourceProforma, Long saleId, Long convertedBy) {
        if (sourceProforma == null) return;

        Long proformaId = sourceProforma.getId();

        // 1) Relación directa en sale
        int saleUpdated = saleV2Repository.updateSourceProformaId(saleId, proformaId);
        if (saleUpdated != 1) {
            throw new InvalidSaleV2Exception("No se pudo registrar source_proforma_id en la venta " + saleId + ".");
        }

        // 2) Relación histórica / compatibilidad
        saleReferenceRepository.create(saleId, proformaId);

        // 3) Relación directa en proforma + estado
        int proformaUpdated = proformaRepository.markAsConverted(proformaId, saleId, convertedBy);
        if (proformaUpdated != 1) {
            throw new InvalidSaleV2Exception(
                    "No se pudo marcar la proforma " + proformaId + " como CONVERTIDA. " +
                    "Probablemente ya fue convertida o anulada."
            );
        }
    }

    private void validateDocumentRules(SaleV2CreateRequest request, Totals totals, List<ComputedLine> lines) {
        if (request.getDocType() == DocType.FACTURA) {
            if (request.getCustomerDocType() == null || !"RUC".equalsIgnoreCase(request.getCustomerDocType())) {
                throw new InvalidSaleV2Exception("FACTURA requiere customerDocType=RUC.");
            }
            if (request.getCustomerDocNumber() == null || request.getCustomerDocNumber().trim().isEmpty()) {
                throw new InvalidSaleV2Exception("FACTURA requiere customerDocNumber (RUC).");
            }
        }

        boolean genericCustomer = isGenericCustomerDocumentType(request.getCustomerDocType());

        if (totals != null && totals.total != null
                && totals.total.compareTo(GENERIC_CUSTOMER_TOTAL_LIMIT) > 0) {
            if (request.getCustomerDocType() == null || request.getCustomerDocType().trim().isEmpty()
                    || request.getCustomerDocNumber() == null || request.getCustomerDocNumber().trim().isEmpty()
                    || genericCustomer) {
                throw new InvalidSaleV2Exception("Ventas mayores a S/ 700.00 requieren cliente identificado. No se permite GEN/0.");
            }
        }

        if (genericCustomer) {
            request.setCustomerDocType("GEN");
            request.setCustomerDocNumber("0");
            if (request.getCustomerName() == null || request.getCustomerName().trim().isEmpty()) {
                request.setCustomerName("VENTA RAPIDA");
            }
        }

        if (request.getDocType() == DocType.BOLETA || request.getDocType() == DocType.FACTURA) {
            if (request.getTaxStatus() == TaxStatus.NO_GRAVADA
                    && (request.getTaxReason() == null || request.getTaxReason().trim().isEmpty())) {
                request.setTaxReason("EXONERADA");
            }

            if (request.getCustomerDocType() == null || request.getCustomerDocType().trim().isEmpty()
                    || request.getCustomerDocNumber() == null || request.getCustomerDocNumber().trim().isEmpty()) {
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


    private List<SaleV2CreateRequest.Item> filterFacturableItemsForSaleFromProforma(
            List<SaleV2CreateRequest.Item> requestItems,
            List<ProformaItem> proformaItems
    ) {
        if (requestItems == null || requestItems.isEmpty()) {
            throw new InvalidSaleV2Exception("Debe enviar items.");
        }

        if (proformaItems == null || proformaItems.isEmpty()) {
            return requestItems;
        }

        Set<Long> nonFacturableProductIds = proformaItems.stream()
                .filter(it -> !Boolean.TRUE.equals(it.getFacturableSunat()))
                .map(ProformaItem::getProductId)
                .filter(Objects::nonNull)
                .collect(java.util.stream.Collectors.toSet());

        if (nonFacturableProductIds.isEmpty()) {
            return requestItems;
        }

        List<SaleV2CreateRequest.Item> filtered = requestItems.stream()
                .filter(Objects::nonNull)
                .filter(it -> it.getProductId() != null)
                .filter(it -> !nonFacturableProductIds.contains(it.getProductId()))
                .toList();

        if (filtered.isEmpty()) {
            throw new InvalidSaleV2Exception(
                    "La proforma no tiene productos facturables para generar una venta."
            );
        }

        return filtered;
    }

    private void processInternalProformaStockMovements(List<ProformaItem> proformaItems) {
        if (proformaItems == null || proformaItems.isEmpty()) {
            return;
        }

        for (ProformaItem item : proformaItems) {
            if (Boolean.TRUE.equals(item.getFacturableSunat())) {
                continue;
            }

            if (!Boolean.TRUE.equals(item.getAffectsStock())) {
                continue;
            }

            if (item.getProductId() == null) {
                throw new InvalidSaleV2Exception("Ítem interno de proforma sin productId.");
            }

            BigDecimal quantity = nz(item.getQuantity());
            if (quantity.compareTo(BigDecimal.ZERO) <= 0) {
                throw new InvalidSaleV2Exception(
                        "Cantidad inválida para ítem interno de proforma. SKU=" + nzs(item.getSku())
                );
            }

            ProductSnapshot product = productSnapshotRepository.findSnapshotById(item.getProductId());
            if (product == null) {
                throw new InvalidSaleV2Exception("Producto interno no encontrado: " + item.getProductId());
            }

            if (Boolean.TRUE.equals(product.getManageBySerial())) {
                throw new InvalidSaleV2Exception(
                        "Producto interno no facturable con serie/VIN no soportado en conversión. SKU=" + nzs(product.getSku())
                );
            }

            BigDecimal unitCost = costPolicy == CostPolicy.PROMEDIO
                    ? nz(productStockRepository.getAverageCost(product.getId()))
                    : nz(productStockRepository.getLastUnitCost(product.getId()));

            BigDecimal totalCost = unitCost.multiply(quantity).setScale(4, RoundingMode.HALF_UP);

            StockMovementBalance balance = productStockRepository.decreaseOnHandOrFail(
                    product.getId(),
                    quantity
            );

            productStockMovementRepository.createOutProformaInternal(
                    product.getId(),
                    quantity,
                    item.getId(),
                    unitCost,
                    totalCost,
                    balance.getQuantityOnHand(),
                    nz(balance.getAverageCost(), unitCost)
            );
        }
    }

    private void validateRequest(SaleV2CreateRequest request) {
        if (request == null) throw new InvalidSaleV2Exception("Request vacío.");

        // stationId es opcional.
        // Para ventas desde contrato no debe depender de caja/estación.
        // Las ventas normales de caja se controlan más abajo con requiresCashSession().
        if (request.getDocType() == null) throw new InvalidSaleV2Exception("docType es obligatorio.");

        request.setSeries(resolveSeriesForRequest(request.getDocType(), request.getSeries()));

        if (request.getPriceList() == null) throw new InvalidSaleV2Exception("priceList es obligatorio.");

        if (request.getTaxStatus() == null) request.setTaxStatus(TaxStatus.NO_GRAVADA);

        if (request.getTaxStatus() == TaxStatus.NO_GRAVADA
                && (request.getTaxReason() == null || request.getTaxReason().trim().isEmpty())) {
            request.setTaxReason("EXONERADA");
        }

        if (request.getIgvIncluded() == null) request.setIgvIncluded(Boolean.FALSE);
        if (request.getTaxStatus() != TaxStatus.GRAVADA) request.setIgvIncluded(Boolean.FALSE);

        if (request.getPaymentType() == null) throw new InvalidSaleV2Exception("paymentType es obligatorio.");
        if (request.getItems() == null || request.getItems().isEmpty()) throw new InvalidSaleV2Exception("Debe enviar items.");
    }

    private String resolveSeriesForRequest(DocType docType, String series) {
        if (docType == DocType.BOLETA || docType == DocType.FACTURA) {
            return documentSeriesPolicy.requireAllowed(docType.name(), series, InvalidSaleV2Exception::new);
        }
        return documentSeriesPolicy.normalizeSeries(series, InvalidSaleV2Exception::new);
    }

    private Totals calculateTotals(SaleV2CreateRequest request, List<ComputedLine> lines) {
        boolean igvIncluded = Boolean.TRUE.equals(request.getIgvIncluded()) && request.getTaxStatus() == TaxStatus.GRAVADA;

        BigDecimal subtotal = BigDecimal.ZERO;       // BASE
        BigDecimal discountTotal = BigDecimal.ZERO;  // descuento aplicado sobre gross (si igvIncluded=true)
        BigDecimal giftCostTotal = BigDecimal.ZERO;

        BigDecimal igvAmount = BigDecimal.ZERO;
        BigDecimal total = BigDecimal.ZERO;

        BigDecimal igvRate = nz(request.getIgvRate(), new BigDecimal("18.00"));

        for (ComputedLine l : lines) {
            subtotal = subtotal.add(l.getRevenueTotal());
            discountTotal = discountTotal.add(l.getDiscountAmount());

            if (l.getLineKind() == LineKind.OBSEQUIO && l.getTotalCostSnapshot() != null) {
                giftCostTotal = giftCostTotal.add(l.getTotalCostSnapshot());
            }

            if (igvIncluded) {
                igvAmount = igvAmount.add(l.getIgvLine());
                total = total.add(l.getGrossAfterDiscount());
            } else {
                total = total.add(l.getRevenueTotal());
            }
        }

        subtotal = subtotal.setScale(4, RoundingMode.HALF_UP);
        discountTotal = discountTotal.setScale(4, RoundingMode.HALF_UP);
        giftCostTotal = giftCostTotal.setScale(4, RoundingMode.HALF_UP);

        if (request.getTaxStatus() == TaxStatus.GRAVADA && !igvIncluded) {
            igvAmount = subtotal.multiply(igvRate).divide(new BigDecimal("100"), 4, RoundingMode.HALF_UP);
            total = subtotal.add(igvAmount).setScale(4, RoundingMode.HALF_UP);
        } else if (request.getTaxStatus() != TaxStatus.GRAVADA) {
            igvAmount = BigDecimal.ZERO;
            total = subtotal.setScale(4, RoundingMode.HALF_UP);
        } else {
            // GRAVADA + igvIncluded=true
            igvAmount = igvAmount.setScale(4, RoundingMode.HALF_UP);
            total = total.setScale(4, RoundingMode.HALF_UP);
        }

        return new Totals(subtotal, discountTotal, igvAmount, total, giftCostTotal);
    }

    private static boolean isGenericCustomerDocumentType(String value) {
        String v = nzs(value).trim().toUpperCase();
        return switch (v) {
            case "GEN", "GENERICO", "GENÉRICO", "GENERAL", "0",
                 "OTROS", "SIN_DOCUMENTO", "SIN DOCUMENTO" -> true;
            default -> false;
        };
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

        /**
         * BASE de la línea (con descuento aplicado)
         */
        private BigDecimal revenueTotal;

        /**
         * Si igvIncluded=true: IGV por línea
         */
        private BigDecimal igvLine;

        /**
         * Si igvIncluded=true: total final de línea (gross after discount)
         */
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
