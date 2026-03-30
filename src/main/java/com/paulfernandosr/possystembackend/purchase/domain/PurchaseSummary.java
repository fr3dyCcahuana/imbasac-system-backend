package com.paulfernandosr.possystembackend.purchase.domain;

import lombok.*;

import java.math.BigDecimal;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PurchaseSummary {
    private Integer itemsCount;
    private BigDecimal totalQuantity;
    private Integer serialUnitsCount;
}
