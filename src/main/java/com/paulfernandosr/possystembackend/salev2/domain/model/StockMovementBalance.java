package com.paulfernandosr.possystembackend.salev2.domain.model;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;

@Getter
@Builder
public class StockMovementBalance {
    private BigDecimal quantityOnHand;
    private BigDecimal averageCost;
    private BigDecimal lastUnitCost;
}
