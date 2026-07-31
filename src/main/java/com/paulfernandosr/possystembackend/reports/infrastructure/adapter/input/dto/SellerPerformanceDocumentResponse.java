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
public class SellerPerformanceDocumentResponse {
    private String source;
    private String sourceLabel;
    private Long documentId;
    private String series;
    private Long number;
    private String documentCode;
    private LocalDate issueDate;
    private BigDecimal quantity;
    private BigDecimal eligibleSales;
    private BigDecimal commissionBase;
    private BigDecimal estimatedCommission;
    private List<SellerPerformanceDocumentLineResponse> products;
}
