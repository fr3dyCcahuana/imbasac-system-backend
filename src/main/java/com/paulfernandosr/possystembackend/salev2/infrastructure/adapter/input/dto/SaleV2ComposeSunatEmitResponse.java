package com.paulfernandosr.possystembackend.salev2.infrastructure.adapter.input.dto;

import lombok.*;

import java.math.BigDecimal;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SaleV2ComposeSunatEmitResponse {
    private Long saleId;
    private String docType;
    private String series;
    private Long number;
    private BigDecimal originalSaleTotal;
    private BigDecimal composedTotal;
    private BigDecimal difference;
    private Boolean exactTotalMatch;
    private SaleV2SunatEmissionResponse emission;
    private List<SaleV2ComposeSunatSourceResponse> linkedCounterSales;
}
