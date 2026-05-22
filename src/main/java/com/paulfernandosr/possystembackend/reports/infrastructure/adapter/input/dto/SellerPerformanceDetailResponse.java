package com.paulfernandosr.possystembackend.reports.infrastructure.adapter.input.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SellerPerformanceDetailResponse {
    private LocalDate from;
    private LocalDate to;
    private Long sellerId;
    private String sellerUsername;
    private String sellerName;
    private BigDecimal incentiveThreshold;
    private BigDecimal eligibleSales;
    private BigDecimal commissionBase;
    private BigDecimal estimatedCommission;
    private BigDecimal score;
    private BigDecimal quantity;
    private Long countDocuments;
    private List<SellerPerformanceCategoryDetailResponse> categories;
    private List<SellerPerformanceProductDetailResponse> products;
}
