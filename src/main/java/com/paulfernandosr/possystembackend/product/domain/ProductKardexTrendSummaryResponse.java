package com.paulfernandosr.possystembackend.product.domain;

import java.math.BigDecimal;

public record ProductKardexTrendSummaryResponse(
        BigDecimal totalIn,
        BigDecimal totalOut,
        BigDecimal netMovement,
        BigDecimal initialStock,
        BigDecimal finalStock,
        BigDecimal stockVariation,
        BigDecimal totalPurchaseAmount,
        BigDecimal totalSalesAmount,
        BigDecimal totalCostIn,
        BigDecimal totalCostOut,
        BigDecimal averageCost,
        Integer periodsCount
) {
}
