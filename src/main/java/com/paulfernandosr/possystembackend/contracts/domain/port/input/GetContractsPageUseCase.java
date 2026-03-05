package com.paulfernandosr.possystembackend.contracts.domain.port.input;

import com.paulfernandosr.possystembackend.salev2.infrastructure.adapter.input.dto.PageResponse;
import com.paulfernandosr.possystembackend.contracts.infrastructure.adapter.input.dto.ContractSummaryResponse;

public interface GetContractsPageUseCase {
    PageResponse<ContractSummaryResponse> findPage(String query, String status, int page, int size);
}
