package com.paulfernandosr.possystembackend.contracts.domain.port.output;

import com.paulfernandosr.possystembackend.contracts.domain.model.ContractItem;

public interface ContractItemRepository {
    Long insert(ContractItem item);
    ContractItem findByContractId(Long contractId);
}
