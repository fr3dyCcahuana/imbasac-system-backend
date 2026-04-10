package com.paulfernandosr.possystembackend.countersale.domain.port.input;

import com.paulfernandosr.possystembackend.countersale.infrastructure.adapter.input.dto.CounterSaleDetailResponse;

public interface GetCounterSaleUseCase {
    CounterSaleDetailResponse getById(Long counterSaleId);
}
