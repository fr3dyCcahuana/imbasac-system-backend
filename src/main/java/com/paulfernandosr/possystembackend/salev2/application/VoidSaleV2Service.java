package com.paulfernandosr.possystembackend.salev2.application;

import com.paulfernandosr.possystembackend.salev2.domain.exception.InvalidSaleV2Exception;
import com.paulfernandosr.possystembackend.salev2.domain.model.VoidSaleV2Response;
import com.paulfernandosr.possystembackend.salev2.domain.port.input.VoidSaleV2UseCase;
import com.paulfernandosr.possystembackend.salev2.domain.port.output.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Service
@RequiredArgsConstructor
public class VoidSaleV2Service implements VoidSaleV2UseCase {

    private final SaleV2Repository saleRepository;
    private final SalePaymentRepository salePaymentRepository;
    private final AccountsReceivableRepository accountsReceivableRepository;
    private final AccountsReceivablePaymentRepository arPaymentRepository;
    private final CustomerAccountRepository customerAccountRepository;

    private final ProductStockRepository productStockRepository;
    private final ProductStockMovementRepository productStockMovementRepository;
    private final ProductSerialUnitRepository productSerialUnitRepository;
    private final SaleSessionAccumulatorRepository saleSessionAccumulatorRepository;

    @Override
    @Transactional
    public VoidSaleV2Response voidSale(Long saleId, String reason) {
        if (saleId == null) {
            throw new InvalidSaleV2Exception("saleId es requerido");
        }

        SaleV2Repository.LockedSale sale = saleRepository.lockById(saleId);
        if (sale == null) {
            throw new InvalidSaleV2Exception("Venta no encontrada: " + saleId);
        }

        if (!"EMITIDA".equalsIgnoreCase(sale.getStatus())) {
            throw new InvalidSaleV2Exception("Solo se puede anular una venta EMITIDA. Estado actual: " + sale.getStatus());
        }

        // 1) Items
        List<SaleV2Repository.SaleItemForVoid> items = saleRepository.findItemsBySaleId(saleId);

        // 2) Reversa de seriales: DEVUELTO + unlink sale_item_id
        List<Long> saleItemIds = items.stream().map(SaleV2Repository.SaleItemForVoid::getId).toList();
        var serials = productSerialUnitRepository.lockBySaleItemIds(saleItemIds);
        for (var su : serials) {
            // Regla de negocio: al anular una venta con serial, pasa a DEVUELTO.
            productSerialUnitRepository.markAsReturned(su.getId());
        }

        // 3) Reversa de stock + kardex IN_RETURN
        for (var it : items) {
            if (Boolean.TRUE.equals(it.getAffectsStock())) {
                BigDecimal qty = it.getQuantity() == null ? BigDecimal.ZERO : it.getQuantity();
                if (qty.signum() <= 0) continue;

                productStockRepository.increaseOnHand(it.getProductId(), qty);

                BigDecimal unitCost = it.getUnitCostSnapshot();
                if (unitCost == null) unitCost = BigDecimal.ZERO;
                BigDecimal totalCost = it.getTotalCostSnapshot();
                if (totalCost == null) {
                    totalCost = unitCost.multiply(qty);
                }

                productStockMovementRepository.createInReturn(it.getProductId(), qty, it.getId(), unitCost, totalCost);
            }
        }

        // 4) Reversa de cobro/credito
        if ("CONTADO".equalsIgnoreCase(sale.getPaymentType())) {
            salePaymentRepository.deleteBySaleId(saleId);
        } else if ("CREDITO".equalsIgnoreCase(sale.getPaymentType())) {
            var ar = accountsReceivableRepository.lockBySaleId(saleId);
            if (ar != null) {
                boolean hasPayments = arPaymentRepository.existsByArId(ar.getId());
                if (hasPayments || (ar.getPaidAmount() != null && ar.getPaidAmount().signum() > 0)) {
                    throw new InvalidSaleV2Exception("No se puede anular: la CxC ya tiene pagos registrados.");
                }
                // Mantener consistencia sin cambiar constraint de status: eliminar CxC si no tuvo pagos.
                accountsReceivableRepository.deleteBySaleId(saleId);
            }
        }

        // 5) Ajuste de sesión de ventas (se mantiene módulo de sesiones)
        if (sale.getSaleSessionId() != null) {
            BigDecimal total = sale.getTotal() == null ? BigDecimal.ZERO : sale.getTotal();
            BigDecimal discount = sale.getDiscountTotal() == null ? BigDecimal.ZERO : sale.getDiscountTotal();
            saleSessionAccumulatorRepository.subtractSaleIncomeAndDiscount(sale.getSaleSessionId(), total, discount);
        }

        // 6) Marcar venta anulada
        String voidNote = (reason == null || reason.isBlank()) ? "ANULADA" : "ANULADA: " + reason;
        saleRepository.markAsVoided(saleId, voidNote);

        // 7) Recalcular cuenta del cliente (si aplica)
        if (sale.getCustomerId() != null) {
            customerAccountRepository.recalculate(sale.getCustomerId());
        }

        return VoidSaleV2Response.builder()
                .saleId(saleId)
                .status("ANULADA")
                .build();
    }
}
