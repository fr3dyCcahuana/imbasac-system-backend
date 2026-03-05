package com.paulfernandosr.possystembackend.contracts.domain.port.input;

import com.paulfernandosr.possystembackend.contracts.infrastructure.adapter.input.dto.ContractDocumentResponse;
import com.paulfernandosr.possystembackend.contracts.infrastructure.adapter.input.dto.ContractCreateRequest;

public interface CreateContractUseCase {
    ContractDocumentResponse create(ContractCreateRequest request, String username);
}
