package com.paulfernandosr.possystembackend.salev2.infrastructure.adapter.input.dto;

import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AccountsReceivablePaymentInfo {
    private Long id;
    private BigDecimal amount;
    private String method;
    private LocalDateTime paidAt;
    private String note;
}
