package com.paulfernandosr.possystembackend.contracts.domain.model;

import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ContractInstallment {

    private Long id;
    private Long contractId;

    private Integer installmentNumber;
    private LocalDate dueDate;

    private BigDecimal amount;

    private BigDecimal paidAmount;
    private String status; // PENDIENTE/PAGADO/ANULADO
}
