package com.paulfernandosr.possystembackend.productoffer.infrastructure.adapter.input.dto;

import java.time.LocalDate;
import java.util.List;

public record ProductBasicOfferRequest(
        String name,
        LocalDate startsAt,
        LocalDate endsAt,
        String status,
        List<ProductBasicOfferItemRequest> items
) {
}
