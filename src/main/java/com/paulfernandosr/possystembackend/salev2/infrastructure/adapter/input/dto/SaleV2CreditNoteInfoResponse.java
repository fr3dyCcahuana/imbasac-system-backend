package com.paulfernandosr.possystembackend.salev2.infrastructure.adapter.input.dto;

import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SaleV2CreditNoteInfoResponse {
    private String status;
    private Integer count;
    private Integer acceptedCount;
    private BigDecimal total;
    private BigDecimal acceptedTotal;
    private String documentsLabel;
    private String lastSeries;
    private Long lastNumber;
    private String lastSunatStatus;
    private LocalDateTime lastEmittedAt;
}
