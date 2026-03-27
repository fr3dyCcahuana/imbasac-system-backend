package com.paulfernandosr.possystembackend.salev2.infrastructure.adapter.input.dto;

import lombok.*;

import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SaleV2SummaryItemResponse {
    private Long saleId;
    private BigDecimal quantity;
    private String sku;
    private String description;
    private String presentation;
}