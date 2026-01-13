package com.paulfernandosr.possystembackend.salev2.domain.model;

import lombok.*;

import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CustomerAccountSnapshot {
    private Long customerId;
    private boolean creditEnabled;
    private BigDecimal creditLimit;
    private BigDecimal currentDebt;
    private BigDecimal overdueDebt;
    private String status; // OK / BLOQUEADO / MOROSO
}
