package com.paulfernandosr.possystembackend.contracts.domain.port.output;

import com.paulfernandosr.possystembackend.contracts.infrastructure.adapter.input.dto.ContractDetailResponse;
import com.paulfernandosr.possystembackend.contracts.infrastructure.adapter.input.dto.ContractSummaryResponse;

import java.util.List;

public interface ContractQueryRepository {

    long count(String likeParam, String status);

    List<ContractSummaryResponse> findPage(String likeParam, String status, int limit, int offset);

    ContractDetailResponse findDetail(Long contractId);
}
