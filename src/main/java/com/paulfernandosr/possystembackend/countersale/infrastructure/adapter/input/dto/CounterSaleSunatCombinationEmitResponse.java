package com.paulfernandosr.possystembackend.countersale.infrastructure.adapter.input.dto;

import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CounterSaleSunatCombinationEmitResponse {
    private Long comboId;
    private Long generatedSaleId;
    private String customerMode;
    private String docType;
    private String series;
    private Long number;
    private LocalDate issueDate;
    private CounterSaleSunatCombinationCustomerResponse customer;
    private String currency;
    private String taxStatus;
    private BigDecimal igvRate;
    private Boolean igvIncluded;
    private BigDecimal composedSubtotal;
    private BigDecimal composedDiscountTotal;
    private BigDecimal composedIgvAmount;
    private BigDecimal composedTotal;
    private BigDecimal totalLimit;
    private BigDecimal remainingLimit;
    private Boolean withinLimit;
    private LocalDateTime associatedAt;
    private CounterSaleSunatEmissionInfoResponse emission;
    private List<CounterSaleSunatCombinationCounterSaleResponse> linkedCounterSales;
    private List<CounterSaleSunatCombinationLineResponse> lines;
}
