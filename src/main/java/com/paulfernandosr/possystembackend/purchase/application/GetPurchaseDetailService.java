package com.paulfernandosr.possystembackend.purchase.application;

import com.paulfernandosr.possystembackend.purchase.domain.Purchase;
import com.paulfernandosr.possystembackend.purchase.domain.exception.InvalidPurchaseException;
import com.paulfernandosr.possystembackend.purchase.domain.port.input.GetPurchaseDetailUseCase;
import com.paulfernandosr.possystembackend.purchase.domain.port.output.PurchaseRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class GetPurchaseDetailService implements GetPurchaseDetailUseCase {

    private final PurchaseRepository purchaseRepository;

    @Override
    public Purchase getPurchaseById(Long purchaseId) {
        return purchaseRepository.findByIdWithItems(purchaseId)
                .orElseThrow(() -> new InvalidPurchaseException("Compra no encontrada con id: " + purchaseId));
    }
}
