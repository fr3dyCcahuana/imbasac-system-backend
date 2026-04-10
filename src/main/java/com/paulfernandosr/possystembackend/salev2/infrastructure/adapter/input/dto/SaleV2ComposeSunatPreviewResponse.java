package com.paulfernandosr.possystembackend.salev2.infrastructure.adapter.input.dto;

import lombok.*;

import java.math.BigDecimal;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SaleV2ComposeSunatPreviewResponse {
    private Long saleId;
    private String docType;
    private String series;
    private Long number;
    private String taxStatus;
    private BigDecimal igvRate;
    private Boolean igvIncluded;
    private BigDecimal originalSaleTotal;
    private BigDecimal composedSubtotal;
    private BigDecimal composedDiscountTotal;
    private BigDecimal composedIgvAmount;
    private BigDecimal composedTotal;
    private BigDecimal difference;
    private Boolean exactTotalMatch;
    private List<SaleV2ComposeSunatSourceResponse> counterSales;
    private List<SaleV2ComposeSunatLineResponse> lines;
}
