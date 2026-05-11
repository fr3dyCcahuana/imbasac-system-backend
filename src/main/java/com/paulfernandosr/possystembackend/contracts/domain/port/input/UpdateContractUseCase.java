package com.paulfernandosr.possystembackend.contracts.domain.port.input;

import com.paulfernandosr.possystembackend.contracts.infrastructure.adapter.input.dto.ContractDetailResponse;
import com.paulfernandosr.possystembackend.contracts.infrastructure.adapter.input.dto.ContractUpdateRequest;

public interface UpdateContractUseCase {
    ContractDetailResponse update(Long contractId, ContractUpdateRequest request, String username);
}
