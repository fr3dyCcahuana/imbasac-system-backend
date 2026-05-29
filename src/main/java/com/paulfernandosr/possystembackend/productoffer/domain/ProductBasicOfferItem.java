package com.paulfernandosr.possystembackend.productoffer.domain;

import java.math.BigDecimal;

public record ProductBasicOfferItem(
        Long id,
        Long productId,
        String sku,
        String productName,
        String presentation,
        String brand,
        String model,
        String category,
        String compatibility,
        BigDecimal regularPrice,
        BigDecimal offerPrice,
        BigDecimal minQuantity,
        BigDecimal stock,
        String imageUrl,
        Integer sortOrder
) {
}
