package com.paulfernandosr.possystembackend.product.domain;

import java.util.List;

public record ProductKardexTrendResponse(
        ProductKardexTrendProductResponse product,
        ProductKardexTrendFiltersResponse filters,
        ProductKardexTrendSummaryResponse summary,
        List<ProductKardexTrendPointResponse> points,
        ProductKardexTrendChartResponse chart
) {
}
