package com.paulfernandosr.possystembackend.contracts.domain.port.input;

import com.paulfernandosr.possystembackend.contracts.infrastructure.adapter.input.dto.ContractDetailResponse;

public interface GetContractUseCase {
    ContractDetailResponse getById(Long contractId);
}
