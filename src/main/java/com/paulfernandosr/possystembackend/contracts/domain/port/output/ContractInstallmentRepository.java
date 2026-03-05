package com.paulfernandosr.possystembackend.contracts.domain.port.output;

import com.paulfernandosr.possystembackend.contracts.domain.model.ContractInstallment;

import java.time.LocalDate;
import java.util.List;

public interface ContractInstallmentRepository {
    void insertBatch(Long contractId, List<ContractInstallment> rows);
    List<ContractInstallment> findByContractId(Long contractId);
    LocalDate findLastDueDate(Long contractId);
}
