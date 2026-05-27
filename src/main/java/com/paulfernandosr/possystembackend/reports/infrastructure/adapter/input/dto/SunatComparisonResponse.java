package com.paulfernandosr.possystembackend.reports.infrastructure.adapter.input.dto;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;

@Getter
@Builder
public class SunatComparisonResponse {
    private String source;
    private String label;
    private BigDecimal commercialTotal;
    private BigDecimal sunatTotal;
    private BigDecimal difference;
    private BigDecimal estimatedTaxSaving;
    private BigDecimal commercialTaxedTotal;
    private BigDecimal commercialNonTaxedTotal;
    private BigDecimal sunatTaxedTotal;
    private BigDecimal sunatNonTaxedTotal;
    private BigDecimal taxedDifference;
    private BigDecimal nonTaxedDifference;
    private BigDecimal estimatedNonTaxedTaxSaving;
    private BigDecimal estimatedTotalTaxSaving;
    private Long countSales;
}
