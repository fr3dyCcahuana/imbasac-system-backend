package com.paulfernandosr.possystembackend.salev2.domain.port.input;

import com.paulfernandosr.possystembackend.salev2.infrastructure.adapter.input.dto.SaleV2CreateRequest;
import com.paulfernandosr.possystembackend.salev2.infrastructure.adapter.input.dto.SaleV2DocumentResponse;

public interface CreateSaleV2UseCase {
    SaleV2DocumentResponse create(SaleV2CreateRequest request, String username);
}
