package com.paulfernandosr.possystembackend.product.domain;

import java.time.LocalDate;

public record ProductKardexTrendFilter(
        Long productId,
        LocalDate dateFrom,
        LocalDate dateTo,
        KardexTrendGroupBy groupBy,
        KardexSource source,
        String movementType,
        Boolean includeEmptyPeriods
) {
}
