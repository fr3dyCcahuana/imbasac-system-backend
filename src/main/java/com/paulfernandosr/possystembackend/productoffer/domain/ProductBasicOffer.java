package com.paulfernandosr.possystembackend.productoffer.domain;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public record ProductBasicOffer(
        Long id,
        String code,
        String name,
        LocalDate startsAt,
        LocalDate endsAt,
        String status,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        List<ProductBasicOfferItem> items
) {
}
