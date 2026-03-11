package com.paulfernandosr.possystembackend.contracts.infrastructure.adapter.input.dto;

import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ContractInstallmentResponse {
    private Integer installmentNumber;
    private LocalDate dueDate;
    private BigDecimal amount;
    private BigDecimal paidAmount;

    // ✅ auditoría
    private java.time.LocalDateTime paidAt;
    private String paidByUsername;

    private String status;
}
