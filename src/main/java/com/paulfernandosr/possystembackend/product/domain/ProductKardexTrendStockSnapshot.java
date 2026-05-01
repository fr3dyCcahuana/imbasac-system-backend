package com.paulfernandosr.possystembackend.product.domain;

import java.math.BigDecimal;

public record ProductKardexTrendStockSnapshot(
        BigDecimal stock,
        BigDecimal averageCost
) {
}
