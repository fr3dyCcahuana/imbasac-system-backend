package com.paulfernandosr.possystembackend.product.domain;

import java.math.BigDecimal;

public record ProductKardexTrendPointResponse(
        String period,
        String periodLabel,
        BigDecimal quantityIn,
        BigDecimal quantityOut,
        BigDecimal netQuantity,
        BigDecimal initialStock,
        BigDecimal finalStock,
        BigDecimal averageCost,
        BigDecimal purchaseAmount,
        BigDecimal salesAmount,
        BigDecimal totalCostIn,
        BigDecimal totalCostOut,
        Integer movementCount
) {
}
