package com.paulfernandosr.possystembackend.salev2.infrastructure.adapter.input.dto;

import lombok.*;

import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ContractSunatDraftItemResponse {

    private Long id;
    private Long saleItemId;
    private Integer lineNumber;

    private Long productId;
    private String sku;
    private String description;

    private BigDecimal quantity;

    private BigDecimal originalUnitPrice;
    private BigDecimal sunatUnitPrice;

    private BigDecimal originalRevenueTotal;
    private BigDecimal sunatRevenueTotal;
}
