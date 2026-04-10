package com.paulfernandosr.possystembackend.countersale.domain.port.input;

import com.paulfernandosr.possystembackend.countersale.infrastructure.adapter.input.dto.CounterSaleCreateRequest;
import com.paulfernandosr.possystembackend.countersale.infrastructure.adapter.input.dto.CounterSaleDocumentResponse;

public interface CreateCounterSaleUseCase {
    CounterSaleDocumentResponse create(CounterSaleCreateRequest request, String username);
}
