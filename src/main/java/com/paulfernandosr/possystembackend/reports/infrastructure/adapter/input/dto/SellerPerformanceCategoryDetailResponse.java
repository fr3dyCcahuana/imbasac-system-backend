package com.paulfernandosr.possystembackend.reports.infrastructure.adapter.input.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SellerPerformanceCategoryDetailResponse {
    private String incentiveGroup;
    private BigDecimal commissionRate;
    private BigDecimal quantity;
    private BigDecimal eligibleSales;
    private BigDecimal commissionBase;
    private BigDecimal estimatedCommission;
    private Long countDocuments;
}
