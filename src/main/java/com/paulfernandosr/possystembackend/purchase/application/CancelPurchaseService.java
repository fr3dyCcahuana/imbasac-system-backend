package com.paulfernandosr.possystembackend.purchase.application;

import com.paulfernandosr.possystembackend.purchase.domain.Purchase;
import com.paulfernandosr.possystembackend.purchase.domain.PurchaseItem;
import com.paulfernandosr.possystembackend.purchase.domain.port.input.CancelPurchaseUseCase;
import com.paulfernandosr.possystembackend.purchase.domain.port.output.PurchaseRepository;
import com.paulfernandosr.possystembackend.stock.domain.port.input.StockService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CancelPurchaseService implements CancelPurchaseUseCase {

    private final PurchaseRepository purchaseRepository;
    private final StockService stockService;

    @Override
    @Transactional
    public void cancelPurchaseById(Long purchaseId, String username) {

        String actor = (username == null || username.isBlank()) ? "SYSTEM" : username.trim();

        Purchase purchase = purchaseRepository.findByIdWithItems(purchaseId)
                .orElseThrow(() -> new IllegalArgumentException("Compra no encontrada"));

        // Validar estado actual
        if ("ANULADA".equalsIgnoreCase(purchase.getStatus())) {
            return;
        }

        // Registrar salidas de stock por cada ítem
        if (purchase.getItems() != null) {
            for (PurchaseItem item : purchase.getItems()) {
                stockService.registerOutbound(
                        item.getProductId(),
                        item.getQuantity(),
                        null, // usamos averageCost actual
                        "OUT_CANCEL_PURCHASE",
                        "purchase_item",
                        item.getId()
                );
            }
        }

        // Cambiar estado
        purchaseRepository.updateStatus(purchaseId, "ANULADA", actor);
    }
}
