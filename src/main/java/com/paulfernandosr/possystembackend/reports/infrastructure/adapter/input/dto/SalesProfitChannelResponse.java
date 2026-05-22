package com.paulfernandosr.possystembackend.reports.infrastructure.adapter.input.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SalesProfitChannelResponse {
    private String source;
    private String label;
    private SalesProfitChannelPointResponse totals;
    private List<SalesProfitChannelPointResponse> points;
}
