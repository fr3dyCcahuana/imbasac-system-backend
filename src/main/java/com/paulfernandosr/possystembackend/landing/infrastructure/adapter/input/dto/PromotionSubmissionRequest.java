package com.paulfernandosr.possystembackend.landing.infrastructure.adapter.input.dto;

import java.time.LocalDateTime;
import java.util.List;

public record PromotionSubmissionRequest(
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
        List<PromotionSubmissionItemRequest> items
) {
}
