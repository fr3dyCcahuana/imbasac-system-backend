package com.paulfernandosr.possystembackend.salev2.infrastructure.adapter.input.dto;

import lombok.*;

import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SaleV2ComposeSunatLineResponse {
    private String sourceType;
    private String sourceDocumentLabel;
    private Long sourceDocumentId;
    private Long sourceItemId;
    private Integer sourceLineNumber;
    private Long productId;
    private String sku;
    private String description;
    private BigDecimal quantity;
    private BigDecimal originalUnitPrice;
    private BigDecimal composedUnitPrice;
    private BigDecimal discountPercent;
    private BigDecimal originalRevenueTotal;
    private BigDecimal composedRevenueTotal;
}
