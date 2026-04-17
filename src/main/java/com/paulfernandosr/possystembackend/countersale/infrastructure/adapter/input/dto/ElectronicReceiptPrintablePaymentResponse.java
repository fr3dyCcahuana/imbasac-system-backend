package com.paulfernandosr.possystembackend.countersale.infrastructure.adapter.input.dto;

import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ElectronicReceiptPrintablePaymentResponse {
    private String method;
    private BigDecimal amount;
    private LocalDateTime paidAt;
}
