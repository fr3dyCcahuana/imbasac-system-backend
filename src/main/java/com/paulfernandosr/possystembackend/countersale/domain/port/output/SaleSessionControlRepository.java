package com.paulfernandosr.possystembackend.countersale.domain.port.output;

import com.paulfernandosr.possystembackend.countersale.domain.model.OpenSaleSession;

public interface SaleSessionControlRepository {
    OpenSaleSession findOpenByUserId(Long userId);
}
