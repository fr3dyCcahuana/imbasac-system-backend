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
public class SellerPerformanceResponse {
    private LocalDate from;
    private LocalDate to;
    private String groupBy;
    private BigDecimal incentiveThreshold;
    private List<SellerPerformanceRowResponse> rows;
}
