package com.paulfernandosr.possystembackend.purchase.domain.port.output;

import com.paulfernandosr.possystembackend.common.domain.Page;
import com.paulfernandosr.possystembackend.common.domain.Pageable;
import com.paulfernandosr.possystembackend.purchase.domain.Purchase;

import java.util.Optional;

public interface PurchaseRepository {

    Purchase create(Purchase purchase, String username);                   // inserta cabecera + items

    Page<Purchase> findPage(String query, Pageable pageable); // solo cabecera

    Optional<Purchase> findByIdWithItems(Long purchaseId);    // cabecera + items

    void updateStatus(Long purchaseId, String status, String username);        // REGISTRADA / ANULADA
}
