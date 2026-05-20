package com.paulfernandosr.possystembackend.landing.infrastructure.adapter.input.dto;

import java.math.BigDecimal;

public record PromotionSubmissionItemRequest(
        String code,
        String description,
        BigDecimal quantity,
        BigDecimal price
) {
}
