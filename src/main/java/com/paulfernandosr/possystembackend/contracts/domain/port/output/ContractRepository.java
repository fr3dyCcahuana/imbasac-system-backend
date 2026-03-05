package com.paulfernandosr.possystembackend.contracts.domain.port.output;

import com.paulfernandosr.possystembackend.contracts.domain.model.Contract;
import com.paulfernandosr.possystembackend.contracts.domain.model.ContractStatus;

public interface ContractRepository {

    Long insert(Contract contract);

    Contract findById(Long id);

    void updateStatusAndSale(Long contractId, ContractStatus status, Long saleId, String notes);

    void updateStatus(Long contractId, ContractStatus status, String notes);
}
