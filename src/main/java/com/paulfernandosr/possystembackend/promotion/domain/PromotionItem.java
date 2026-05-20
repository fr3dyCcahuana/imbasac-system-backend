package com.paulfernandosr.possystembackend.promotion.domain;

import java.math.BigDecimal;

public record PromotionItem(
        Long id,
        Long productId,
        String sku,
        String productName,
        String brand,
        String model,
        String category,
        String role,
        BigDecimal quantity,
        BigDecimal price,
        BigDecimal promoPrice,
        BigDecimal discountPercent,
        Boolean required,
        Integer sortOrder,
        BigDecimal stock,
        String imageUrl
) {
}
