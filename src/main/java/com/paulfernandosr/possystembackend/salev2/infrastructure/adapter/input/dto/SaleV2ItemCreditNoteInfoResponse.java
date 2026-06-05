package com.paulfernandosr.possystembackend.salev2.infrastructure.adapter.input.dto;

import lombok.*;

import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SaleV2ItemCreditNoteInfoResponse {
    private String status;
    private Integer count;
    private BigDecimal creditedQuantity;
    private BigDecimal acceptedQuantity;
    private BigDecimal pendingQuantity;
    private BigDecimal creditedTotal;
    private BigDecimal acceptedTotal;
    private BigDecimal returnedToStockQuantity;
    private String documentsLabel;
    private String lastSunatStatus;
}
