package com.paulfernandosr.possystembackend.product.domain;

import java.util.List;

public record ProductKardexTrendChartResponse(
        List<String> labels,
        ProductKardexTrendDatasetsResponse datasets
) {
}
