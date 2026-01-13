package com.paulfernandosr.possystembackend.salev2.application;

import com.paulfernandosr.possystembackend.salev2.domain.exception.InvalidSaleV2Exception;
import com.paulfernandosr.possystembackend.salev2.domain.model.*;
import com.paulfernandosr.possystembackend.salev2.domain.port.input.CreateSaleV2UseCase;
import com.paulfernandosr.possystembackend.salev2.domain.port.output.DocumentSeriesRepository;
import com.paulfernandosr.possystembackend.salev2.domain.port.output.ProductSnapshotRepository;
import com.paulfernandosr.possystembackend.salev2.domain.port.output.ProductStockMovementRepository;
import com.paulfernandosr.possystembackend.salev2.domain.port.output.ProductStockRepository;
import com.paulfernandosr.possystembackend.salev2.domain.port.output.ProductCostRepository;
import com.paulfernandosr.possystembackend.salev2.domain.port.output.SaleV2Repository;
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
import java.util.List;

@Service
@RequiredArgsConstructor
public class CreateSaleV2Service implements CreateSaleV2UseCase {

    private final UserRepository userRepository;
    private final DocumentSeriesRepository documentSeriesRepository;
    private final ProductSnapshotRepository productSnapshotRepository;
    private final ProductStockRepository productStockRepository;
    private final ProductStockMovementRepository productStockMovementRepository;
    private final ProductCostRepository productCostRepository;
    private final SaleV2Repository saleV2Repository;

    // Política de costo snapshot para reportes (PROMEDIO=average_cost, ULTIMO=last_unit_cost)
    private final CostPolicy costPolicy = CostPolicy.PROMEDIO;

    @Override
    @Transactional
    public SaleV2DocumentResponse create(SaleV2CreateRequest request, String username) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new InvalidSaleV2Exception("Usuario inválido: " + username));

        // Mantener regla de sesión (no tocar tu módulo de sesión actual)
        if (user.isNotOnRegister()) {
            throw new InvalidSaleV2Exception("El usuario no tiene una sesión de ventas abierta: " + username);
        }

        validateRequest(request);

        DocType docType = request.getDocType();

        // 1) Reservar correlativo con bloqueo
        DocumentSeriesRepository.LockedSeries locked = documentSeriesRepository
                .lockAndGetNextNumber(request.getStationId(), docType, request.getSeries());

        // 2) Insert cabecera
        Long saleId = saleV2Repository.insertSale(
                request.getStationId(),
                null, // sale_session_id se integra luego (sin romper la sesión actual)
                user.getId(),
                docType,
                locked.series(),
                locked.number(),
                defaultIfNull(request.getIssueDate(), LocalDate.now()),
                defaultIfBlank(request.getCurrency(), "PEN"),
                request.getExchangeRate(),
                request.getPriceList(),
                request.getCustomerId(),
                request.getCustomerDocType(),
                request.getCustomerDocNumber(),
                request.getCustomerName(),
                request.getCustomerAddress(),
                defaultIfNull(request.getTaxStatus(), TaxStatus.NO_GRAVADA),
                null,
                defaultIfNull(request.getIgvRate(), new BigDecimal("18.00")),
                request.getPaymentType(),
                null,
                null,
                request.getNotes()
        );

        // 3) Items + totales
        Totals totals = persistItemsAndComputeTotals(
                saleId,
                docType,
                request.getPriceList(),
                request.getTaxStatus(),
                defaultIfNull(request.getIgvRate(), new BigDecimal("18.00")),
                request.getItems()
        );

        saleV2Repository.updateTotals(
                saleId,
                totals.subtotal,
                totals.discountTotal,
                totals.igvAmount,
                totals.total,
                BigDecimal.ZERO
        );

        // 4) Pago (Paso 1: solo contado)
        if (request.getPaymentType() == PaymentType.CONTADO) {
            String method = request.getPayment() != null ? request.getPayment().getMethod() : null;
            saleV2Repository.insertPayment(saleId, method, totals.total);
        } else {
            throw new InvalidSaleV2Exception("Paso 1: solo se admite CONTADO. CREDITO se habilita en el siguiente entregable.");
        }

        // 5) Actualizar sesión (misma lógica que tu implementación anterior)
        saleV2Repository.addIncomeToOpenSession(user.getId(), totals.total, totals.discountTotal);

        // 6) Confirmar correlativo
        documentSeriesRepository.incrementNextNumber(locked.id());

        return SaleV2DocumentResponse.builder()
                .saleId(saleId)
                .docType(docType)
                .series(locked.series())
                .number(locked.number())
                .currency(defaultIfBlank(request.getCurrency(), "PEN"))
                .total(totals.total)
                .build();
    }

    private Totals persistItemsAndComputeTotals(
            Long saleId,
            DocType docType,
            PriceList priceList,
            TaxStatus taxStatus,
            BigDecimal igvRate,
            List<SaleV2CreateRequest.Item> items
    ) {
        if (items == null || items.isEmpty()) {
            throw new InvalidSaleV2Exception("La venta debe tener al menos 1 ítem.");
        }

        BigDecimal subtotal = BigDecimal.ZERO;       // suma de (qty * unit_price) para VENDIDO/OBSEQUIO (obsequio será 0)
        BigDecimal discountTotal = BigDecimal.ZERO;  // suma descuentos
        BigDecimal giftCostTotal = BigDecimal.ZERO;  // suma costos de obsequios (solo ítems que afectan stock)

        int lineNumber = 1;
        for (SaleV2CreateRequest.Item item : items) {
            if (item.getProductId() == null) {
                throw new InvalidSaleV2Exception("Cada ítem debe tener productId.");
            }

            ProductSnapshot product = productSnapshotRepository.findSnapshotById(item.getProductId())
                    .orElseThrow(() -> new InvalidSaleV2Exception("Producto no encontrado: " + item.getProductId()));

            BigDecimal qty = normalizeQty(item.getQuantity());

            LineKind kind = defaultIfNull(item.getLineKind(), LineKind.VENDIDO);

            // Precio snapshot por lista A/B/C/D (Regla #3)
            BigDecimal unitPrice;
            if (kind == LineKind.OBSEQUIO) {
                if (!product.isGiftAllowed()) {
                    throw new InvalidSaleV2Exception("Producto no permite obsequio (gift_allowed=false): " + product.getSku());
                }
                if (item.getGiftReason() == null || item.getGiftReason().isBlank()) {
                    throw new InvalidSaleV2Exception("giftReason es obligatorio para ítems OBSEQUIO.");
                }
                unitPrice = BigDecimal.ZERO;
            } else {
                unitPrice = resolveUnitPrice(product, priceList);
            }

            BigDecimal gross = scale(qty.multiply(unitPrice));

            BigDecimal discountPercent = defaultIfNull(item.getDiscountPercent(), BigDecimal.ZERO);
            if (discountPercent.compareTo(BigDecimal.ZERO) < 0) {
                throw new InvalidSaleV2Exception("discountPercent no puede ser negativo.");
            }

            BigDecimal discountAmount = scale(gross.multiply(discountPercent).divide(new BigDecimal("100"), 6, RoundingMode.HALF_UP));
            if (discountAmount.compareTo(gross) > 0) {
                discountAmount = gross;
            }

            // Regla de visible_in_document (Regla #4)
            boolean visibleInDocument = switch (docType) {
                case BOLETA, FACTURA -> product.isFacturableSunat();
                case SIMPLE -> true;
            };

            // Costo snapshot + stock/kardex (Reglas #5 y #8)
            boolean affectsStock = product.isAffectsStock();
            BigDecimal unitCostSnapshot = null;
            BigDecimal totalCostSnapshot = null;

            if (affectsStock) {
                unitCostSnapshot = productCostRepository.getUnitCost(product.getId(), costPolicy).setScale(6, RoundingMode.HALF_UP);
                totalCostSnapshot = scale(unitCostSnapshot.multiply(qty));
            }

            BigDecimal revenueTotal = (kind == LineKind.OBSEQUIO)
                    ? BigDecimal.ZERO
                    : scale(gross.subtract(discountAmount));

            Long saleItemId = saleV2Repository.insertSaleItem(
                    saleId,
                    lineNumber++,
                    product.getId(),
                    product.getSku(),
                    product.getName(),
                    product.getPresentation(),
                    product.getFactor(),
                    qty,
                    unitPrice,
                    discountPercent,
                    discountAmount,
                    kind.name(),
                    item.getGiftReason(),
                    product.isFacturableSunat(),
                    product.isAffectsStock(),
                    visibleInDocument,
                    unitCostSnapshot,
                    totalCostSnapshot,
                    revenueTotal
            );

            // PROFORMA nunca llega aquí (este use case es solo ventas),
            // pero reforzamos que SOLO SIMPLE/BOLETA/FACTURA generan stock/kardex.
            if (docType != DocType.SIMPLE && docType != DocType.BOLETA && docType != DocType.FACTURA) {
                throw new InvalidSaleV2Exception("docType inválido para ventas: " + docType);
            }

            if (affectsStock) {
                // 1) Validar + descontar stock (seguro por UPDATE ... WHERE on_hand>=qty)
                productStockRepository.decreaseOnHand(product.getId(), qty);

                // 2) Kardex OUT_SALE
                productStockMovementRepository.createOutSaleMovement(product.getId(), qty, saleItemId);

                // 3) Acumulador costo obsequios
                if (kind == LineKind.OBSEQUIO && totalCostSnapshot != null) {
                    giftCostTotal = giftCostTotal.add(totalCostSnapshot);
                }
            }

            subtotal = subtotal.add(gross);
            discountTotal = discountTotal.add(discountAmount);
        }

        subtotal = scale(subtotal);
        discountTotal = scale(discountTotal);

        BigDecimal base = subtotal.subtract(discountTotal);
        if (base.compareTo(BigDecimal.ZERO) < 0) {
            base = BigDecimal.ZERO;
        }

        BigDecimal igvAmount = BigDecimal.ZERO;
        if (taxStatus == TaxStatus.GRAVADA) {
            igvAmount = scale(base.multiply(igvRate).divide(new BigDecimal("100"), 6, RoundingMode.HALF_UP));
        }

        BigDecimal total = scale(base.add(igvAmount));
        giftCostTotal = scale(giftCostTotal);

        return new Totals(subtotal, discountTotal, igvAmount, total, giftCostTotal);
    }


    private void validateRequest(SaleV2CreateRequest request) {
        if (request.getStationId() == null) {
            throw new InvalidSaleV2Exception("stationId es obligatorio");
        }
        if (request.getDocType() == null) {
            throw new InvalidSaleV2Exception("docType es obligatorio");
        }
        if (request.getSeries() == null || request.getSeries().isBlank()) {
            throw new InvalidSaleV2Exception("series es obligatorio");
        }
        if (request.getPriceList() == null) {
            throw new InvalidSaleV2Exception("priceList es obligatorio (A/B/C/D)");
        }
        if (request.getPaymentType() == null) {
            throw new InvalidSaleV2Exception("paymentType es obligatorio (CONTADO/CREDITO)");
        }
        if (request.getDocType() == DocType.FACTURA) {
            if (request.getCustomerDocType() == null || !"RUC".equalsIgnoreCase(request.getCustomerDocType())) {
                throw new InvalidSaleV2Exception("FACTURA exige cliente con RUC");
            }
            if (request.getCustomerDocNumber() == null || request.getCustomerDocNumber().isBlank()) {
                throw new InvalidSaleV2Exception("FACTURA exige customerDocNumber (RUC)");
            }
        }
    }

    private static BigDecimal getPriceByList(ProductSnapshot p, PriceList list) {
        return switch (list) {
            case A -> defaultIfNull(p.getPriceA(), BigDecimal.ZERO);
            case B -> defaultIfNull(p.getPriceB(), BigDecimal.ZERO);
            case C -> defaultIfNull(p.getPriceC(), BigDecimal.ZERO);
            case D -> defaultIfNull(p.getPriceD(), BigDecimal.ZERO);
        };
    }

    private static BigDecimal normalizeQty(BigDecimal qty) {
        if (qty == null || qty.compareTo(BigDecimal.ZERO) <= 0) {
            throw new InvalidSaleV2Exception("quantity debe ser > 0");
        }
        return qty.setScale(3, RoundingMode.HALF_UP);
    }

    private static BigDecimal scale(BigDecimal value) {
        return value.setScale(4, RoundingMode.HALF_UP);
    }

    private static String defaultIfBlank(String val, String def) {
        return (val == null || val.isBlank()) ? def : val;
    }

    private static <T> T defaultIfNull(T val, T def) {
        return val == null ? def : val;
    }

    private record Totals(BigDecimal subtotal, BigDecimal discountTotal, BigDecimal igvAmount, BigDecimal total, BigDecimal giftCostTotal) {}
}
