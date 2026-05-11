package com.paulfernandosr.possystembackend.contracts.domain.port.output;

import com.paulfernandosr.possystembackend.contracts.domain.model.Contract;
import com.paulfernandosr.possystembackend.contracts.domain.model.ContractStatus;

public interface ContractRepository {

    Long insert(Contract contract);

    Contract findById(Long id);

    Contract lockById(Long id);

    void updateEditableFields(Contract contract, Long editedBy, String editedByUsername, String editReason);

    void updateStatusAndSale(Long contractId, ContractStatus status, Long saleId, String notes);

    void updateStatus(Long contractId, ContractStatus status, String notes);
}
