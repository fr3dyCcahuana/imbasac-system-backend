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
    private Long countSales;
}
