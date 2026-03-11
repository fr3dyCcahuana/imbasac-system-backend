package com.paulfernandosr.possystembackend.contracts.domain.port.output;

import com.paulfernandosr.possystembackend.contracts.domain.model.ContractInstallment;

import java.time.LocalDate;
import java.math.BigDecimal;
import java.util.List;

public interface ContractInstallmentRepository {
    void insertBatch(Long contractId, List<ContractInstallment> rows);
    List<ContractInstallment> findByContractId(Long contractId);
    LocalDate findLastDueDate(Long contractId);

    // ✅ pagos por cuota exacta
    LockedInstallment lockByContractIdAndNumber(Long contractId, int installmentNumber);

    void updatePaidAmountAndStatus(Long contractId, int installmentNumber, BigDecimal paidAmount, String status,
                                 java.time.LocalDateTime paidAt, Long paidBy, String paidByUsername);


    class LockedInstallment {
        private Long id;
        private BigDecimal amount;
        private BigDecimal paidAmount;
        private String status;

        public LockedInstallment() {}

        public LockedInstallment(Long id, BigDecimal amount, BigDecimal paidAmount, String status) {
            this.id = id;
            this.amount = amount;
            this.paidAmount = paidAmount;
            this.status = status;
        }

        public Long getId() { return id; }
        public BigDecimal getAmount() { return amount; }
        public BigDecimal getPaidAmount() { return paidAmount; }
        public String getStatus() { return status; }
    }
}
