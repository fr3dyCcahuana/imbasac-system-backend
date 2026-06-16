package com.paulfernandosr.possystembackend.purchase.domain.port.output;

import com.paulfernandosr.possystembackend.common.domain.Page;
import com.paulfernandosr.possystembackend.common.domain.Pageable;
import com.paulfernandosr.possystembackend.purchase.domain.Purchase;
import com.paulfernandosr.possystembackend.purchase.domain.PurchaseItem;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface PurchaseRepository {

    Purchase create(Purchase purchase, String username);                   // inserta cabecera + items

    Page<Purchase> findPage(String query, Pageable pageable);              // solo cabecera

    Optional<Purchase> findByIdWithItems(Long purchaseId);                 // cabecera + items activos

    Optional<Purchase> findByIdWithItemsForUpdate(Long purchaseId);         // cabecera + items activos, bloqueo de cabecera

    List<Long> findPendingStockEntryPurchaseIds(LocalDate dueDate);

    void updateStatus(Long purchaseId, String status, String username);     // REGISTRADA / ANULADA

    void markStockEntryPending(Long purchaseId);

    void markStockEntryLoaded(Long purchaseId, String username);

    boolean existsDocumentForAnotherPurchase(Long purchaseId,
                                             String supplierRuc,
                                             String documentType,
                                             String documentSeries,
                                             String documentNumber);

    void updateHeaderForEdit(Purchase purchase, String username, String editReason);

    Long insertItem(Long purchaseId, PurchaseItem item);

    void updateItemForEdit(Long purchaseId, PurchaseItem item);

    void markItemRemoved(Long purchaseId, Long purchaseItemId, String username, String editReason);

    BigDecimal findStockOnHand(Long productId);

    int getNextEditNumber(Long purchaseId);

    void insertEditHistory(Long purchaseId,
                           int editNumber,
                           String editReason,
                           String editedBy,
                           String beforeSnapshotJson,
                           String afterSnapshotJson);
}
