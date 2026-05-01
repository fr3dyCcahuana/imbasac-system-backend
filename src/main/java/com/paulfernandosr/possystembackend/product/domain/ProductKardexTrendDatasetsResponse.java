package com.paulfernandosr.possystembackend.product.domain;

import java.math.BigDecimal;
import java.util.List;

public record ProductKardexTrendDatasetsResponse(
        List<BigDecimal> quantityIn,
        List<BigDecimal> quantityOut,
        List<BigDecimal> netQuantity,
        List<BigDecimal> initialStock,
        List<BigDecimal> finalStock,
        List<BigDecimal> purchaseAmount,
        List<BigDecimal> salesAmount,
        List<BigDecimal> averageCost
) {
}
