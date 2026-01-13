package com.paulfernandosr.possystembackend.salev2.infrastructure.adapter.input.dto;

import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AccountsReceivablePaymentResponse {
    private Long arId;
    private BigDecimal paidAmount;
    private BigDecimal balanceAmount;
    private String status;
    private LocalDate dueDate;
}
