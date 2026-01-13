package com.paulfernandosr.possystembackend.purchase.domain.port.input;

public interface CancelPurchaseUseCase {
    void cancelPurchaseById(Long purchaseId);
}
