package com.paulfernandosr.possystembackend.contracts.domain.port.output;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public interface ContractPaymentRepository {
    Long insert(Long contractId,
                String paymentType,
                Integer installmentNumber,
                BigDecimal amount,
                String method,
                LocalDateTime paidAt,
                String note,
                Long createdBy,
                String createdByUsername);

    BigDecimal sumPaidByContractId(Long contractId);
}
