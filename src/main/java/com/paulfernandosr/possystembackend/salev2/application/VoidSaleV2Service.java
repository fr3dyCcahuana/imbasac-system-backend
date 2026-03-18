package com.paulfernandosr.possystembackend.salev2.application;

import com.paulfernandosr.possystembackend.salev2.domain.exception.InvalidSaleV2Exception;
import com.paulfernandosr.possystembackend.salev2.domain.model.OpenSaleSession;
import com.paulfernandosr.possystembackend.salev2.domain.model.VoidSaleV2Response;
import com.paulfernandosr.possystembackend.salev2.domain.port.input.VoidSaleV2UseCase;
import com.paulfernandosr.possystembackend.salev2.domain.port.output.*;
import com.paulfernandosr.possystembackend.user.domain.User;
import com.paulfernandosr.possystembackend.user.domain.port.output.UserRepository;
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
    private final UserRepository userRepository;
    private final SaleSessionControlRepository saleSessionControlRepository;

    @Override
    @Transactional
    public VoidSaleV2Response voidSale(Long saleId, String reason, String username) {
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

        List<SaleV2Repository.SaleItemForVoid> items = saleRepository.findItemsBySaleId(saleId);

        List<Long> saleItemIds = items.stream().map(SaleV2Repository.SaleItemForVoid::getId).toList();
        var serials = productSerialUnitRepository.lockBySaleItemIds(saleItemIds);
        for (var su : serials) {
            productSerialUnitRepository.markAsReturned(su.getId());
        }

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

        if ("CONTADO".equalsIgnoreCase(sale.getPaymentType())) {
            salePaymentRepository.deleteBySaleId(saleId);

            User user = userRepository.findByUsername(username)
                    .orElseThrow(() -> new InvalidSaleV2Exception("Usuario inválido: " + username));
            if (user.isNotOnRegister()) {
                throw new InvalidSaleV2Exception("El usuario no tiene una sesión de caja abierta para registrar la reversa.");
            }

            OpenSaleSession openSession = saleSessionControlRepository.findOpenByUserId(user.getId());
            if (openSession == null) {
                throw new InvalidSaleV2Exception("No se encontró una sesión de caja abierta para registrar la reversa.");
            }

            BigDecimal total = sale.getTotal() == null ? BigDecimal.ZERO : sale.getTotal();
            saleSessionAccumulatorRepository.addExpense(openSession.getId(), total);

        } else if ("CREDITO".equalsIgnoreCase(sale.getPaymentType())) {
            var ar = accountsReceivableRepository.lockBySaleId(saleId);
            if (ar != null) {
                boolean hasPayments = arPaymentRepository.existsByArId(ar.getId());
                if (hasPayments || (ar.getPaidAmount() != null && ar.getPaidAmount().signum() > 0)) {
                    throw new InvalidSaleV2Exception("No se puede anular: la CxC ya tiene pagos registrados.");
                }
                accountsReceivableRepository.deleteBySaleId(saleId);
            }
        }

        String voidNote = (reason == null || reason.isBlank()) ? "ANULADA" : "ANULADA: " + reason;
        saleRepository.markAsVoided(saleId, voidNote);

        if (sale.getCustomerId() != null) {
            customerAccountRepository.recalculate(sale.getCustomerId());
        }

        return VoidSaleV2Response.builder()
                .saleId(saleId)
                .status("ANULADA")
                .build();
    }
}
