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
public class ProfitPeriodResponse {
    private LocalDate periodStart;
    private BigDecimal revenueNetIgv;
    private BigDecimal totalCogs;
    private BigDecimal grossProfit;
}
