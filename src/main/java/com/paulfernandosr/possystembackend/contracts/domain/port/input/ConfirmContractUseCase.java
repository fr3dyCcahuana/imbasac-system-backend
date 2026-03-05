package com.paulfernandosr.possystembackend.contracts.domain.port.input;

import com.paulfernandosr.possystembackend.contracts.infrastructure.adapter.input.dto.ContractDetailResponse;

public interface ConfirmContractUseCase {
    ContractDetailResponse confirm(Long contractId, String username);
}
