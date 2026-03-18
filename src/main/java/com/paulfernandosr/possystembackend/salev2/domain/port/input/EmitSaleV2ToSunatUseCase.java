package com.paulfernandosr.possystembackend.salev2.domain.port.input;

import com.paulfernandosr.possystembackend.salev2.infrastructure.adapter.input.dto.SaleV2SunatEmissionResponse;

public interface EmitSaleV2ToSunatUseCase {
    SaleV2SunatEmissionResponse emit(Long saleId);
}
