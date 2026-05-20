package com.paulfernandosr.possystembackend.landing.domain;

import java.math.BigDecimal;
import java.util.List;

public record LandingOrder(
        Long id,
        String series,
        Long number,
        String customerName,
        String phone,
        String status,
        BigDecimal total,
        List<LandingOrderItem> items
) {
}
