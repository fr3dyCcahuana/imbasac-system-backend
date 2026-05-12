package com.paulfernandosr.possystembackend.salev2.domain.port.input;

import com.paulfernandosr.possystembackend.salev2.infrastructure.adapter.input.dto.ContractSunatDraftEmissionResponse;

public interface EmitContractSunatDraftUseCase {
    ContractSunatDraftEmissionResponse emit(Long saleId, String username);
}
