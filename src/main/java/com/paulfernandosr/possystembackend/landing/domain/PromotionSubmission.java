package com.paulfernandosr.possystembackend.landing.domain;

import java.time.LocalDateTime;

public record PromotionSubmission(
        Long id,
        String code,
        String businessName,
        String contactName,
        String phone,
        String documentNumber,
        String type,
        String title,
        String description,
        String imageUrl,
        LocalDateTime startsAt,
        LocalDateTime endsAt,
        String status,
        LocalDateTime createdAt
) {
}
