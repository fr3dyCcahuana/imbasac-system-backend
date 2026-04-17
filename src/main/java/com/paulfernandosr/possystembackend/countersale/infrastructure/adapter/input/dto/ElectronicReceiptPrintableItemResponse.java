package com.paulfernandosr.possystembackend.countersale.infrastructure.adapter.input.dto;

import lombok.*;

import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ElectronicReceiptPrintableItemResponse {
    private Long saleItemId;
    private Integer lineNumber;
    private Long productId;
    private String sku;
    private String description;
    private String presentation;
    private BigDecimal quantity;
    private BigDecimal unitPrice;
    private BigDecimal discountAmount;
    private BigDecimal revenueTotal;
}
