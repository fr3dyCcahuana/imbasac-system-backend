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
public class ProductSaleDetailResponse {
    private String source;
    private String sourceLabel;
    private Long documentId;
    private String series;
    private Long number;
    private LocalDate issueDate;
    private String customerName;
    private BigDecimal quantity;
    private BigDecimal totalSales;
}
