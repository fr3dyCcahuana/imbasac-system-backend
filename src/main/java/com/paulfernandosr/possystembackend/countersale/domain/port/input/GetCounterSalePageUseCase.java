package com.paulfernandosr.possystembackend.countersale.domain.port.input;

import com.paulfernandosr.possystembackend.countersale.infrastructure.adapter.input.dto.CounterSaleSummaryResponse;
import com.paulfernandosr.possystembackend.countersale.infrastructure.adapter.input.dto.PageResponse;

public interface GetCounterSalePageUseCase {
    PageResponse<CounterSaleSummaryResponse> findPage(String query,
                                                     String series,
                                                     Long number,
                                                     String status,
                                                     int page,
                                                     int size);
}
