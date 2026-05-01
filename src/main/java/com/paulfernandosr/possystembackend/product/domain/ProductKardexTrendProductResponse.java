package com.paulfernandosr.possystembackend.product.domain;

public record ProductKardexTrendProductResponse(
        Long productId,
        String sku,
        String productName,
        String category,
        String brand,
        String model,
        String presentation,
        Boolean manageBySerial
) {
}
