package com.paulfernandosr.possystembackend.countersale.infrastructure.adapter.input.dto;

import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CounterSaleSunatCombinationValidationResponse {
    private Long anchorCounterSaleId;
    private String customerMode;
    private String docType;
    private String series;
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
    private Boolean canEmit;
    private List<String> validationMessages;
    private List<CounterSaleSunatCombinationCounterSaleResponse> counterSales;
    private List<CounterSaleSunatCombinationLineResponse> lines;
}
