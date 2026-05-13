package com.paulfernandosr.possystembackend.contracts.application;

import com.paulfernandosr.possystembackend.contracts.domain.exception.InvalidContractException;
import com.paulfernandosr.possystembackend.contracts.domain.port.input.GenerateSaleFromContractUseCase;
import com.paulfernandosr.possystembackend.contracts.infrastructure.adapter.input.dto.ContractGenerateSaleRequest;
import com.paulfernandosr.possystembackend.contracts.infrastructure.adapter.input.dto.ContractGenerateSaleResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class GenerateSaleFromContractService implements GenerateSaleFromContractUseCase {

    @Override
    public ContractGenerateSaleResponse generateSale(Long contractId,
                                                     ContractGenerateSaleRequest req,
                                                     String username) {
        throw new InvalidContractException(
                "Flujo retirado: los contratos ya no generan venta interna ni reservan B/F antes de SUNAT. " +
                "Use /contracts/{id}/sunat-draft y /contracts/{id}/sunat-draft/emit."
        );
    }
}
