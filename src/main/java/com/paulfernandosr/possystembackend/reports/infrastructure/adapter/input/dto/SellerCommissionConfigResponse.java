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
public class SellerCommissionConfigResponse {
    private Long sellerId;
    private BigDecimal monthlyGoal;
    private BigDecimal bajajRate;
    private BigDecimal ktmRate;
    private BigDecimal imbaRate;
    private BigDecimal bonRate;
    private boolean customized;
}

