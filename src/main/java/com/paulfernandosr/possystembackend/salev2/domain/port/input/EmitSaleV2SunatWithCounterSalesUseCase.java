package com.paulfernandosr.possystembackend.salev2.domain.port.input;

import com.paulfernandosr.possystembackend.salev2.infrastructure.adapter.input.dto.SaleV2ComposeSunatEmitResponse;
import com.paulfernandosr.possystembackend.salev2.infrastructure.adapter.input.dto.SaleV2ComposeSunatRequest;

public interface EmitSaleV2SunatWithCounterSalesUseCase {
    SaleV2ComposeSunatEmitResponse emit(Long saleId, SaleV2ComposeSunatRequest request, String username);
}
