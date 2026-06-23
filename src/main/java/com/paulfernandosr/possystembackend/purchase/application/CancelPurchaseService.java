package com.paulfernandosr.possystembackend.purchase.application;

import com.paulfernandosr.possystembackend.purchase.domain.Purchase;
import com.paulfernandosr.possystembackend.purchase.domain.PurchaseItem;
import com.paulfernandosr.possystembackend.purchase.domain.exception.PurchaseApiException;
import com.paulfernandosr.possystembackend.purchase.domain.model.ProductFlags;
import com.paulfernandosr.possystembackend.purchase.domain.port.input.CancelPurchaseUseCase;
import com.paulfernandosr.possystembackend.purchase.domain.port.output.ProductFlagsRepository;
import com.paulfernandosr.possystembackend.purchase.domain.port.output.ProductSerialUnitRepository;
import com.paulfernandosr.possystembackend.purchase.domain.port.output.PurchaseRepository;
import com.paulfernandosr.possystembackend.stock.domain.port.input.StockService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@Service
@RequiredArgsConstructor
public class CancelPurchaseService implements CancelPurchaseUseCase {

    private final PurchaseRepository purchaseRepository;
    private final ProductFlagsRepository productFlagsRepository;
    private final ProductSerialUnitRepository productSerialUnitRepository;
    private final StockService stockService;

    @Override
    @Transactional
    public void cancelPurchaseById(Long purchaseId, String username) {

        String actor = (username == null || username.isBlank()) ? "SYSTEM" : username.trim();

        Purchase purchase = purchaseRepository.findByIdWithItemsForUpdate(purchaseId)
                .orElseThrow(() -> new PurchaseApiException(404, "PURCHASE_NOT_FOUND", "Compra no encontrada."));

        if ("ANULADA".equalsIgnoreCase(purchase.getStatus())) {
            return;
        }
        boolean stockAlreadyLoaded = !PurchaseStockEntryService.STATUS_PENDING.equalsIgnoreCase(purchase.getStockEntryStatus());

        int blockedSerials = productSerialUnitRepository.countBlockedSerialUnitsByPurchaseId(purchaseId);
        if (blockedSerials > 0) {
            throw new PurchaseApiException(
                    422,
                    "PURCHASE_SERIALS_ALREADY_USED",
                    "No se puede anular la compra porque uno o más seriales ya fueron vendidos, reservados, usados en contrato o ventanilla."
            );
        }

        if (purchase.getItems() != null) {
            for (PurchaseItem item : purchase.getItems()) {
                ProductFlags flags = productFlagsRepository.findById(item.getProductId()).orElse(null);
                if (flags == null || !Boolean.TRUE.equals(flags.getAffectsStock()) || !stockAlreadyLoaded) {
                    continue;
                }

                BigDecimal stockOnHand = purchaseRepository.findStockOnHand(item.getProductId());
                if (stockOnHand.compareTo(item.getQuantity()) < 0) {
                    throw new PurchaseApiException(
                            422,
                            "INSUFFICIENT_STOCK_TO_CANCEL_PURCHASE",
                            "No se puede anular la compra porque el stock actual del producto "
                                    + item.getProductId()
                                    + " es menor que la cantidad a revertir."
                    );
                }
            }

            for (PurchaseItem item : purchase.getItems()) {
                ProductFlags flags = productFlagsRepository.findById(item.getProductId()).orElse(null);
                if (flags == null || !Boolean.TRUE.equals(flags.getAffectsStock())) {
                    continue;
                }

                if (stockAlreadyLoaded) {
                    stockService.registerOutbound(
                            item.getProductId(),
                            item.getQuantity(),
                            null,
                            "OUT_CANCEL_PURCHASE",
                            "purchase_item",
                            item.getId()
                    );
                }

                if (Boolean.TRUE.equals(flags.getManageBySerial())) {
                    productSerialUnitRepository.markSerialUnitsByPurchaseItemAsBaja(item.getId());
                }
            }
        }

        purchaseRepository.updateStatus(purchaseId, "ANULADA", actor);
        purchaseRepository.refreshProductCostReferencesByPurchase(purchaseId);
    }
}
