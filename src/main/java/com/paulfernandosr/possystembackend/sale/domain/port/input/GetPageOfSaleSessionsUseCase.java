package com.paulfernandosr.possystembackend.sale.domain.port.input;

import com.paulfernandosr.possystembackend.common.domain.Page;
import com.paulfernandosr.possystembackend.common.domain.Pageable;
import com.paulfernandosr.possystembackend.sale.domain.SaleSession;

public interface GetPageOfSaleSessionsUseCase {
    Page<SaleSession> getPageOfSaleSessions(String query, Pageable pageable);
}
