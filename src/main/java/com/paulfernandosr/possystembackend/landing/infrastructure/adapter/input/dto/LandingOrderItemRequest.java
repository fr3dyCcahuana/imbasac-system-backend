package com.paulfernandosr.possystembackend.landing.infrastructure.adapter.input.dto;

import java.math.BigDecimal;

public record LandingOrderItemRequest(
        Long productId,
        Long promotionId,
        BigDecimal quantity,
        BigDecimal unitPrice,
        BigDecimal discountAmount
) {
}
