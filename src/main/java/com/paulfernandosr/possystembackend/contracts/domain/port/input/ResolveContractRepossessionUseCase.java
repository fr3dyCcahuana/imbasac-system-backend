package com.paulfernandosr.possystembackend.contracts.domain.port.input;

import com.paulfernandosr.possystembackend.contracts.infrastructure.adapter.input.dto.ContractRepossessionRequest;
import com.paulfernandosr.possystembackend.contracts.infrastructure.adapter.input.dto.ContractVoidResponse;

public interface ResolveContractRepossessionUseCase {
    ContractVoidResponse resolve(Long contractId, ContractRepossessionRequest request, String username);
}
