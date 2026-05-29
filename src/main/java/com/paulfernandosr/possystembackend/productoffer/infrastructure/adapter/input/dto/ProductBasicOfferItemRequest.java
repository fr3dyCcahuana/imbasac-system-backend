package com.paulfernandosr.possystembackend.productoffer.infrastructure.adapter.input.dto;

import java.math.BigDecimal;

public record ProductBasicOfferItemRequest(
        Long productId,
        BigDecimal minQuantity,
        BigDecimal offerPrice,
        Integer sortOrder
) {
}
