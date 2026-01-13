package com.paulfernandosr.possystembackend.purchase.domain.port.input;

import com.paulfernandosr.possystembackend.common.domain.Page;
import com.paulfernandosr.possystembackend.common.domain.Pageable;
import com.paulfernandosr.possystembackend.purchase.domain.Purchase;

public interface GetPageOfPurchasesUseCase {
    Page<Purchase> getPageOfPurchases(String query, Pageable pageable);
}
