package com.paulfernandosr.possystembackend.countersale.application;

import com.paulfernandosr.possystembackend.countersale.domain.exception.InvalidCounterSaleException;
import com.paulfernandosr.possystembackend.countersale.domain.model.OpenSaleSession;
import com.paulfernandosr.possystembackend.countersale.domain.model.StockMovementBalance;
import com.paulfernandosr.possystembackend.countersale.domain.model.VoidCounterSaleResponse;
import com.paulfernandosr.possystembackend.countersale.domain.port.input.VoidCounterSaleUseCase;
import com.paulfernandosr.possystembackend.countersale.domain.port.output.*;
import com.paulfernandosr.possystembackend.user.domain.User;
import com.paulfernandosr.possystembackend.user.domain.port.output.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Service
@RequiredArgsConstructor
public class VoidCounterSaleService implements VoidCounterSaleUseCase {

    private final CounterSaleRepository counterSaleRepository;
    private final CounterSalePaymentRepository counterSalePaymentRepository;
    private final ProductStockRepository productStockRepository;
    private final ProductStockMovementRepository productStockMovementRepository;
    private final ProductSerialUnitRepository productSerialUnitRepository;
    private final SaleSessionAccumulatorRepository saleSessionAccumulatorRepository;
    private final UserRepository userRepository;
    private final SaleSessionControlRepository saleSessionControlRepository;

    @Override
    @Transactional
    public VoidCounterSaleResponse voidSale(Long counterSaleId, String reason, String username) {
        if (counterSaleId == null) {
            throw new InvalidCounterSaleException("counterSaleId es requerido");
        }

        CounterSaleRepository.LockedCounterSale sale = counterSaleRepository.lockById(counterSaleId);
        if (sale == null) {
            throw new InvalidCounterSaleException("Operación de ventanilla no encontrada: " + counterSaleId);
        }
        if (!"EMITIDA".equalsIgnoreCase(sale.getStatus())) {
            throw new InvalidCounterSaleException("Solo se puede anular una operación EMITIDA. Estado actual: " + sale.getStatus());
        }
        if (Boolean.TRUE.equals(sale.getAssociatedToSunat())) {
            String linkedDoc = buildLinkedDocumentLabel(sale);
            throw new InvalidCounterSaleException("No se puede anular la operación de ventanilla porque ya fue asociada a un comprobante SUNAT" + linkedDoc + ".");
        }

        List<CounterSaleRepository.CounterSaleItemForVoid> items = counterSaleRepository.findItemsByCounterSaleId(counterSaleId);
        List<Long> itemIds = items.stream().map(CounterSaleRepository.CounterSaleItemForVoid::getId).toList();
        var serials = productSerialUnitRepository.lockByCounterSaleItemIds(itemIds);
        for (var serial : serials) {
            productSerialUnitRepository.releaseAfterVoid(serial.getId());
        }

        for (var item : items) {
            if (Boolean.TRUE.equals(item.getAffectsStock())) {
                BigDecimal qty = item.getQuantity() == null ? BigDecimal.ZERO : item.getQuantity();
                if (qty.signum() <= 0) continue;

                StockMovementBalance balance = productStockRepository.increaseOnHand(item.getProductId(), qty);

                BigDecimal unitCost = item.getUnitCostSnapshot() == null ? BigDecimal.ZERO : item.getUnitCostSnapshot();
                BigDecimal totalCost = item.getTotalCostSnapshot() == null ? unitCost.multiply(qty) : item.getTotalCostSnapshot();
                productStockMovementRepository.createInCounterSaleVoid(
                        item.getProductId(),
                        qty,
                        item.getId(),
                        unitCost,
                        totalCost,
                        balance.getQuantityOnHand(),
                        nz(balance.getAverageCost(), unitCost)
                );
            }
        }

        counterSalePaymentRepository.deleteByCounterSaleId(counterSaleId);

        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new InvalidCounterSaleException("Usuario inválido: " + username));
        if (user.isNotOnRegister()) {
            throw new InvalidCounterSaleException("El usuario no tiene una sesión de caja abierta para registrar la reversa.");
        }

        OpenSaleSession openSession = saleSessionControlRepository.findOpenByUserId(user.getId());
        if (openSession == null) {
            throw new InvalidCounterSaleException("No se encontró una sesión de caja abierta para registrar la reversa.");
        }

        BigDecimal total = sale.getTotal() == null ? BigDecimal.ZERO : sale.getTotal();
        saleSessionAccumulatorRepository.addExpense(openSession.getId(), total);

        if (sale.getSaleSessionId() != null) {
            BigDecimal discount = sale.getDiscountTotal() == null ? BigDecimal.ZERO : sale.getDiscountTotal();
            saleSessionAccumulatorRepository.subtractSaleIncomeAndDiscount(sale.getSaleSessionId(), total, discount);
        }

        String voidReason = (reason == null || reason.isBlank()) ? "ANULADA" : reason.trim();
        counterSaleRepository.markAsVoided(counterSaleId, user.getId(), voidReason);

        return VoidCounterSaleResponse.builder()
                .counterSaleId(counterSaleId)
                .status("ANULADA")
                .build();
    }

    private String buildLinkedDocumentLabel(CounterSaleRepository.LockedCounterSale sale) {
        String series = sale.getAssociatedSeries();
        Long number = sale.getAssociatedNumber();
        String docType = sale.getAssociatedDocType();
        if (series == null || series.isBlank() || number == null) {
            return "";
        }
        String prefix = (docType == null || docType.isBlank()) ? "" : (docType.trim().toUpperCase() + " ");
        return ": " + prefix + series.trim().toUpperCase() + "-" + number;
    }
    private static BigDecimal nz(BigDecimal value, BigDecimal fallback) {
        return value == null ? fallback : value;
    }

}
