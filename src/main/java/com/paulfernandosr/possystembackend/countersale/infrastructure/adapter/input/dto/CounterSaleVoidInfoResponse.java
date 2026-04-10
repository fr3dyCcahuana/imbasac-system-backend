package com.paulfernandosr.possystembackend.countersale.infrastructure.adapter.input.dto;

import lombok.*;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CounterSaleVoidInfoResponse {
    private LocalDateTime voidedAt;
    private Long voidedBy;
    private String voidedByUsername;
    private String voidReason;
}
