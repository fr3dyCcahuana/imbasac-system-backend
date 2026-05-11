package com.paulfernandosr.possystembackend.contracts.domain.port.output;

public interface ContractEditAuditRepository {

    void insert(Long contractId,
                Long editedBy,
                String editedByUsername,
                String reason,
                String beforeJson,
                String afterJson);
}
