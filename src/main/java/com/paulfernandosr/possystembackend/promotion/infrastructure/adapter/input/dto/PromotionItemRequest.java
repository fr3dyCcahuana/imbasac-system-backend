package com.paulfernandosr.possystembackend.promotion.infrastructure.adapter.input.dto;

import java.math.BigDecimal;

public record PromotionItemRequest(
        Long productId,
        String role,
        BigDecimal quantity,
        BigDecimal promoPrice,
        BigDecimal discountPercent,
        Boolean required,
        Integer sortOrder,
        BigDecimal stockLimit
) {
}
