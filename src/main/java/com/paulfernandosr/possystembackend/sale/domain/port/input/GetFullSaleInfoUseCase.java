package com.paulfernandosr.possystembackend.sale.domain.port.input;

import com.paulfernandosr.possystembackend.sale.domain.Sale;

public interface GetFullSaleInfoUseCase {
    Sale getFullSaleInfoById(Long saleSessionId);
}
