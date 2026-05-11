package com.paulfernandosr.possystembackend.purchase.domain.port.input;

import com.paulfernandosr.possystembackend.purchase.domain.Purchase;

public interface UpdatePurchaseUseCase {
    Purchase updatePurchase(Long purchaseId, Purchase purchase, String username);
}
