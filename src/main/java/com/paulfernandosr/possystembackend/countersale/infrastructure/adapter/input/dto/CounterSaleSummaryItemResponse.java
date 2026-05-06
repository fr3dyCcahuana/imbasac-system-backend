package com.paulfernandosr.possystembackend.countersale.infrastructure.adapter.input.dto;

import lombok.*;

import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CounterSaleSummaryItemResponse {
    private Long counterSaleId;
    private Long counterSaleItemId;
    private Integer lineNumber;
    private Long productId;
    private BigDecimal quantity;
    private String sku;
    private String description;
    private String productLocation;
    private String presentation;
    private BigDecimal unitPrice;
    private BigDecimal revenueTotal;
}
