package com.paulfernandosr.possystembackend.purchase.domain.port.input;

import com.paulfernandosr.possystembackend.purchase.domain.Purchase;

public interface CreatePurchaseUseCase {
    Purchase createPurchase(Purchase purchase, String username);
}
