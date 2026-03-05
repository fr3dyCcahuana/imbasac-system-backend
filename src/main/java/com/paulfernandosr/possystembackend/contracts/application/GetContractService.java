package com.paulfernandosr.possystembackend.contracts.application;

import com.paulfernandosr.possystembackend.contracts.domain.port.input.GetContractUseCase;
import com.paulfernandosr.possystembackend.contracts.domain.port.output.ContractQueryRepository;
import com.paulfernandosr.possystembackend.contracts.infrastructure.adapter.input.dto.ContractDetailResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class GetContractService implements GetContractUseCase {

    private final ContractQueryRepository queryRepository;

    @Override
    public ContractDetailResponse getById(Long contractId) {
        return queryRepository.findDetail(contractId);
    }
}
