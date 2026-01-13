package com.paulfernandosr.possystembackend.purchase.application;

import com.paulfernandosr.possystembackend.purchase.domain.Purchase;
import com.paulfernandosr.possystembackend.purchase.domain.PurchaseItem;
import com.paulfernandosr.possystembackend.purchase.domain.port.input.CreatePurchaseUseCase;
import com.paulfernandosr.possystembackend.purchase.domain.port.output.PurchaseRepository;
import com.paulfernandosr.possystembackend.stock.domain.port.input.StockService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@Service
public class RegisterPurchaseWithStockService implements CreatePurchaseUseCase {

    private final PurchaseRepository purchaseRepository;
    private final StockService stockService;

    public RegisterPurchaseWithStockService(PurchaseRepository purchaseRepository,
                                            StockService stockService) {
        this.purchaseRepository = purchaseRepository;
        this.stockService = stockService;
    }

    @Override
    @Transactional
    public Purchase createPurchase(Purchase purchase) {

        validateDeliveryGuide(purchase);
        // a) Aquí podrías recalcular subtotal, IGV, total, prorrateo de flete, etc.
        //    (por ahora asumimos que ya viene calculado desde el frontend)

        // b) Guardar cabecera + detalle
        Purchase created = purchaseRepository.create(purchase);

        // c) Por cada ítem, registrar entrada en stock
        if (created.getItems() != null) {
            for (PurchaseItem item : created.getItems()) {
                Long productId = item.getProductId();
                BigDecimal qty = item.getQuantity();
                BigDecimal unitCost = item.getTotalCost() != null && item.getQuantity() != null
                        && item.getQuantity().compareTo(BigDecimal.ZERO) > 0
                        ? item.getTotalCost().divide(item.getQuantity(), 6, java.math.RoundingMode.HALF_UP)
                        : item.getUnitCost();

                stockService.registerInbound(
                        productId,
                        qty,
                        unitCost,
                        "IN_PURCHASE",
                        "purchase_item",
                        item.getId()  // ojo: si el ID se genera en DB, deberías recuperarlo en el repo
                );
            }
        }

        return created;
    }

    private void validateDeliveryGuide(Purchase purchase) {
        boolean hasAny =
                notBlank(purchase.getDeliveryGuideSeries()) ||
                        notBlank(purchase.getDeliveryGuideNumber()) ||
                        notBlank(purchase.getDeliveryGuideCompany());

        if (hasAny) {
            if (isBlank(purchase.getDeliveryGuideSeries()) ||
                    isBlank(purchase.getDeliveryGuideNumber()) ||
                    isBlank(purchase.getDeliveryGuideCompany())) {
                throw new IllegalArgumentException("La guía de remisión debe incluir serie, número y empresa.");
            }
        }
    }

    private boolean isBlank(String s) { return s == null || s.trim().isEmpty(); }
    private boolean notBlank(String s) { return !isBlank(s); }

}
