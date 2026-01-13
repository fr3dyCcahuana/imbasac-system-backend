package com.paulfernandosr.possystembackend.purchase.domain.port.input;

import com.paulfernandosr.possystembackend.purchase.domain.Purchase;

public interface GetPurchaseDetailUseCase {
    Purchase getPurchaseById(Long purchaseId);
}
