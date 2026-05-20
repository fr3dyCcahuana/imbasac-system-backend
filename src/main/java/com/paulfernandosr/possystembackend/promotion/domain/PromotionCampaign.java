package com.paulfernandosr.possystembackend.promotion.domain;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record PromotionCampaign(
        Long id,
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
        List<PromotionItem> items
) {
}
