package com.paulfernandosr.possystembackend.reports.infrastructure.adapter.input.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SellerPerformanceRowResponse {
    private LocalDate periodStart;
    private String source;
    private String sourceLabel;
    private Long sellerId;
    private String sellerUsername;
    private String sellerName;
    private BigDecimal totalSales;
    private Long countSales;
    private BigDecimal eligibleSales;
    private BigDecimal commissionBase;
    private BigDecimal estimatedCommission;
    private BigDecimal score;
}
