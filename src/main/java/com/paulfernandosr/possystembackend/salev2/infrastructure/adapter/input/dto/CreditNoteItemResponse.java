package com.paulfernandosr.possystembackend.salev2.infrastructure.adapter.input.dto;

import lombok.*;

import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreditNoteItemResponse {
    private Long creditNoteItemId;
    private Long saleItemId;
    private Long productId;
    private String sku;
    private String description;
    private BigDecimal quantity;
    private BigDecimal revenueTotal;
    private Boolean returnedToStock;
}
