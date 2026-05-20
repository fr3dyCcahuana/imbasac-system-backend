package com.paulfernandosr.possystembackend.promotion.infrastructure.adapter.input.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record PromotionCampaignRequest(
        String code,
        String name,
        String slug,
        String kind,
        String type,
        String title,
        String subtitle,
        String description,
        String badge,
        String heroImageUrl,
        String bannerImageUrl,
        String discountType,
        BigDecimal discountValue,
        LocalDateTime startsAt,
        LocalDateTime endsAt,
        String status,
        Integer priority,
        String channel,
        List<PromotionItemRequest> items
) {
}
