package com.paulfernandosr.possystembackend.salev2.domain.port.input;

import com.paulfernandosr.possystembackend.salev2.infrastructure.adapter.input.dto.PageResponse;
import com.paulfernandosr.possystembackend.salev2.infrastructure.adapter.input.dto.SaleV2SummaryResponse;

public interface GetSalesV2PageUseCase {
    PageResponse<SaleV2SummaryResponse> findPage(String query, int page, int size);
}
