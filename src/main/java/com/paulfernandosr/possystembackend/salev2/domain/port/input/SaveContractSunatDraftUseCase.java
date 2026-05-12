package com.paulfernandosr.possystembackend.salev2.domain.port.input;

import com.paulfernandosr.possystembackend.salev2.infrastructure.adapter.input.dto.ContractSunatDraftResponse;
import com.paulfernandosr.possystembackend.salev2.infrastructure.adapter.input.dto.ContractSunatDraftSaveRequest;

public interface SaveContractSunatDraftUseCase {
    ContractSunatDraftResponse save(Long saleId, ContractSunatDraftSaveRequest request, String username);
}
