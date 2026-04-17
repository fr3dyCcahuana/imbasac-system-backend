package com.paulfernandosr.possystembackend.countersale.infrastructure.adapter.input.dto;

import lombok.*;

import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CounterSaleSunatCombinationLineResponse {
    private Long counterSaleId;
    private Long counterSaleItemId;
    private String sourceDocumentLabel;
    private Integer sourceLineNumber;
    private Long productId;
    private String sku;
    private String description;
    private BigDecimal quantity;
    private BigDecimal originalUnitPrice;
    private BigDecimal emittedUnitPrice;
    private BigDecimal discountPercent;
    private BigDecimal discountAmount;
    private BigDecimal originalRevenueTotal;
    private BigDecimal emittedRevenueTotal;
}
