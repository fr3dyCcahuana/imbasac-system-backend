package com.paulfernandosr.possystembackend.contracts.domain.port.input;

import com.paulfernandosr.possystembackend.contracts.infrastructure.adapter.input.dto.ContractGenerateSaleRequest;
import com.paulfernandosr.possystembackend.contracts.infrastructure.adapter.input.dto.ContractGenerateSaleResponse;

public interface GenerateSaleFromContractUseCase {
    ContractGenerateSaleResponse generateSale(Long contractId, ContractGenerateSaleRequest request, String username);
}
