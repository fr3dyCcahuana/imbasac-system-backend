package com.paulfernandosr.possystembackend.product.domain.port.input;

import com.paulfernandosr.possystembackend.product.domain.ProductKardexTrendResponse;

import java.time.LocalDate;

public interface GetProductKardexTrendUseCase {

    ProductKardexTrendResponse getTrend(
            Long productId,
            LocalDate dateFrom,
            LocalDate dateTo,
            String groupBy,
            String source,
            String movementType,
            Boolean includeEmptyPeriods
    );
}
