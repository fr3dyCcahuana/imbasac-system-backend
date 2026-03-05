package com.paulfernandosr.possystembackend.contracts.domain.port.input;

import com.paulfernandosr.possystembackend.contracts.infrastructure.adapter.input.dto.ContractVoidResponse;

public interface VoidContractUseCase {
    ContractVoidResponse voidContract(Long contractId, String reason, String username);
}
