package com.paulfernandosr.possystembackend.salev2.domain.port.input;

import com.paulfernandosr.possystembackend.salev2.infrastructure.adapter.input.dto.SaleV2DetailResponse;

public interface GetSaleV2UseCase {
    SaleV2DetailResponse getById(Long saleId);
}
