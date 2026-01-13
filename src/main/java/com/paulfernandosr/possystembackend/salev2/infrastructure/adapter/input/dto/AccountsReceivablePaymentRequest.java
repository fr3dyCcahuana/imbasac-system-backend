package com.paulfernandosr.possystembackend.salev2.infrastructure.adapter.input.dto;

import com.paulfernandosr.possystembackend.salev2.domain.model.PaymentMethod;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AccountsReceivablePaymentRequest {
    private BigDecimal amount;
    private PaymentMethod method;
    private String note;
    private LocalDateTime paidAt; // opcional
}
