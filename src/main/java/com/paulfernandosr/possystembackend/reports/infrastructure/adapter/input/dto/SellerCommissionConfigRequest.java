package com.paulfernandosr.possystembackend.reports.infrastructure.adapter.input.dto;

import java.math.BigDecimal;

public record SellerCommissionConfigRequest(
        BigDecimal monthlyGoal,
        BigDecimal bajajRate,
        BigDecimal ktmRate,
        BigDecimal imbaRate,
        BigDecimal bonRate
) {
}

