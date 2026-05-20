package com.paulfernandosr.possystembackend.landing.domain;

import java.math.BigDecimal;

public record LandingProduct(
        Long id,
        String sku,
        String name,
        String brand,
        String model,
        String category,
        BigDecimal price,
        BigDecimal stock,
        String imageUrl
) {
}
