package com.paulfernandosr.possystembackend.landing.domain;

import java.math.BigDecimal;

public record LandingOrderItem(
        Long productId,
        Long promotionId,
        String description,
        BigDecimal quantity,
        BigDecimal unitPrice,
        BigDecimal discountAmount,
        BigDecimal total
) {
}
