package com.paulfernandosr.possystembackend.landing.domain;

import com.paulfernandosr.possystembackend.promotion.domain.PromotionCampaign;

import java.util.List;

public record LandingContent(
        String heroTitle,
        String heroSubtitle,
        String whatsappNumber,
        String whatsappMessage,
        List<LandingProduct> featuredProducts,
        List<PromotionCampaign> promotions,
        List<String> categories
) {
}
