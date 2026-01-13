package com.paulfernandosr.possystembackend.sale.domain.port.input;

import com.paulfernandosr.possystembackend.sale.domain.SaleSession;

public interface OpenSaleSessionUseCase {
    void openSaleSession(SaleSession saleSession);
}
