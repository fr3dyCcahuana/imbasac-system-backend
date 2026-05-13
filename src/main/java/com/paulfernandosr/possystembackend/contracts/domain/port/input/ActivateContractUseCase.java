package com.paulfernandosr.possystembackend.contracts.domain.port.input;

import com.paulfernandosr.possystembackend.contracts.infrastructure.adapter.input.dto.ContractActivateRequest;
import com.paulfernandosr.possystembackend.contracts.infrastructure.adapter.input.dto.ContractDetailResponse;

public interface ActivateContractUseCase {
    ContractDetailResponse activate(Long contractId, ContractActivateRequest request, String username);
}
