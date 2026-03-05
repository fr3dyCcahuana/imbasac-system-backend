package com.paulfernandosr.possystembackend.contracts.domain.port.output;

import com.paulfernandosr.possystembackend.contracts.domain.model.ContractCustomerProfile;

public interface ContractCustomerProfileRepository {
    void upsert(Long contractId, ContractCustomerProfile profile);
    ContractCustomerProfile findByContractId(Long contractId);
}
