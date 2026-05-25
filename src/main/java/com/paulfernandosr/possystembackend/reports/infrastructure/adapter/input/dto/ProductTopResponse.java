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
public class ProductTopResponse {
    private Long productId;
    private String productSku;
    private String productName;
    private BigDecimal totalQty;
    private BigDecimal totalSales;
    private BigDecimal totalProfit;
    private BigDecimal stockAvailable;
    private Long countSales;
}
