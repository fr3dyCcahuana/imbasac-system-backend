package com.paulfernandosr.possystembackend.salev2.domain.port.output;

import com.paulfernandosr.possystembackend.salev2.domain.model.OpenSaleSession;

public interface SaleSessionControlRepository {
    OpenSaleSession findOpenByUserId(Long userId);
}
