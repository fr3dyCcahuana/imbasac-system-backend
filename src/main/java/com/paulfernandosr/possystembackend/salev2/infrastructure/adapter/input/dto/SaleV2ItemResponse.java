package com.paulfernandosr.possystembackend.salev2.infrastructure.adapter.input.dto;

import lombok.*;

import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SaleV2ItemResponse {
    private Long saleItemId;
    private Integer lineNumber;
    private Long productId;
    private String sku;
    private String description;
    private String presentation;
    private BigDecimal factor;

    private BigDecimal quantity;

    private BigDecimal unitPrice;
    private BigDecimal discountPercent;
    private BigDecimal discountAmount;

    private String lineKind;
    private String giftReason;

    private Boolean facturableSunat;
    private Boolean affectsStock;
    private Boolean visibleInDocument;

    private BigDecimal unitCostSnapshot;
    private BigDecimal totalCostSnapshot;

    private BigDecimal revenueTotal;
}
