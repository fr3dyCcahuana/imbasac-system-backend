package com.paulfernandosr.possystembackend.purchase.application;

import com.paulfernandosr.possystembackend.common.domain.Page;
import com.paulfernandosr.possystembackend.common.domain.Pageable;
import com.paulfernandosr.possystembackend.purchase.domain.Purchase;
import com.paulfernandosr.possystembackend.purchase.domain.port.input.GetPageOfPurchasesUseCase;
import com.paulfernandosr.possystembackend.purchase.domain.port.output.PurchaseRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class GetPageOfPurchasesService implements GetPageOfPurchasesUseCase {

    private final PurchaseRepository purchaseRepository;

    @Override
    public Page<Purchase> getPageOfPurchases(String query, Pageable pageable) {
        return purchaseRepository.findPage(query, pageable);
    }
}
