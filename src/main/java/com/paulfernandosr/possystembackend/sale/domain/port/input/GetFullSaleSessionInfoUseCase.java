package com.paulfernandosr.possystembackend.sale.domain.port.input;

import com.paulfernandosr.possystembackend.sale.application.query.GetFullSaleSessionInfoQuery;
import com.paulfernandosr.possystembackend.sale.domain.SaleSession;

public interface GetFullSaleSessionInfoUseCase {
    SaleSession getFullSaleSessionInfoById(GetFullSaleSessionInfoQuery query);
}
