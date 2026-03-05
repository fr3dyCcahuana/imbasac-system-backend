package com.paulfernandosr.possystembackend.contracts.domain.port.output;

import com.paulfernandosr.possystembackend.contracts.domain.model.ContractGuarantor;

public interface ContractGuarantorRepository {
    void upsert(Long contractId, ContractGuarantor guarantor);
    ContractGuarantor findByContractId(Long contractId);
}
