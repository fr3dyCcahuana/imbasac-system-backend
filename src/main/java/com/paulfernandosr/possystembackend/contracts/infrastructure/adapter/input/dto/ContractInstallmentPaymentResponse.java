package com.paulfernandosr.possystembackend.contracts.infrastructure.adapter.input.dto;

import com.paulfernandosr.possystembackend.salev2.infrastructure.adapter.input.dto.AccountsReceivablePaymentResponse;
import lombok.*;

import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ContractInstallmentPaymentResponse {
    private Long contractId;
    private Integer installmentNumber;

    private String installmentStatus;
    private BigDecimal installmentPaidAmount;

    private Long saleId;
    private Long arId;

    private AccountsReceivablePaymentResponse receivable;
}
