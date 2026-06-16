package com.paulfernandosr.possystembackend.purchase.application;

import com.paulfernandosr.possystembackend.purchase.domain.Purchase;
import com.paulfernandosr.possystembackend.purchase.domain.PurchaseItem;
import com.paulfernandosr.possystembackend.purchase.domain.PurchaseSerialUnit;
import com.paulfernandosr.possystembackend.purchase.domain.exception.PurchaseApiException;
import com.paulfernandosr.possystembackend.purchase.domain.model.ProductFlags;
import com.paulfernandosr.possystembackend.purchase.domain.port.output.ProductFlagsRepository;
import com.paulfernandosr.possystembackend.purchase.domain.port.output.ProductSerialUnitRepository;
import com.paulfernandosr.possystembackend.purchase.domain.port.output.PurchaseRepository;
import com.paulfernandosr.possystembackend.stock.domain.port.input.StockService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

@Service
@RequiredArgsConstructor
public class PurchaseStockEntryService {

    private static final ZoneId BUSINESS_ZONE = ZoneId.of("America/Lima");
    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_LOADED = "LOADED";

    private final PurchaseRepository purchaseRepository;
    private final ProductFlagsRepository productFlagsRepository;
    private final ProductSerialUnitRepository productSerialUnitRepository;
    private final StockService stockService;

    public boolean isDueForStockEntry(Purchase purchase) {
        LocalDate entryDate = purchase != null ? purchase.getEntryDate() : null;
        return entryDate != null && !entryDate.isAfter(LocalDate.now(BUSINESS_ZONE));
    }

    @Transactional
    public Purchase loadStockIfPending(Long purchaseId, String username) {
        String actor = resolveActor(username);
        Purchase purchase = purchaseRepository.findByIdWithItemsForUpdate(purchaseId)
                .orElseThrow(() -> new PurchaseApiException(404, "PURCHASE_NOT_FOUND", "Compra no encontrada."));

        if (!STATUS_PENDING.equalsIgnoreCase(nullToPending(purchase.getStockEntryStatus()))) {
            return purchase;
        }
        if (!isDueForStockEntry(purchase)) {
            return purchase;
        }

        for (PurchaseItem item : safeItems(purchase)) {
            ProductFlags flags = productFlagsRepository.findById(item.getProductId()).orElse(null);
            if (flags == null || !Boolean.TRUE.equals(flags.getAffectsStock())) {
                continue;
            }

            stockService.registerInbound(
                    item.getProductId(),
                    item.getQuantity(),
                    item.getUnitCost(),
                    "IN_PURCHASE",
                    "purchase_item",
                    item.getId()
            );

            if (Boolean.TRUE.equals(flags.getManageBySerial())) {
                if (hasPendingSerials(item)) {
                    productSerialUnitRepository.markSerialUnitsByPurchaseItemAsInWarehouse(item.getId());
                } else {
                    productSerialUnitRepository.insertInboundSerialUnits(
                            item.getId(),
                            item.getProductId(),
                            item.getSerialUnits()
                    );
                }
            }
        }

        purchaseRepository.markStockEntryLoaded(purchaseId, actor);
        return purchaseRepository.findByIdWithItems(purchaseId).orElse(purchase);
    }

    private static boolean hasPendingSerials(PurchaseItem item) {
        if (item == null || item.getSerialUnits() == null) return false;
        return item.getSerialUnits().stream()
                .map(PurchaseSerialUnit::getStatus)
                .anyMatch(status -> "PENDIENTE_INGRESO".equalsIgnoreCase(status));
    }

    private static List<PurchaseItem> safeItems(Purchase purchase) {
        return purchase == null || purchase.getItems() == null ? List.of() : purchase.getItems();
    }

    private static String nullToPending(String status) {
        return status == null || status.isBlank() ? STATUS_PENDING : status;
    }

    private static String resolveActor(String username) {
        return (username == null || username.isBlank()) ? "SYSTEM" : username.trim();
    }
}
