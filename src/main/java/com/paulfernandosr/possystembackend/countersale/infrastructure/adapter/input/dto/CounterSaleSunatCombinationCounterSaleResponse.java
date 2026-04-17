package com.paulfernandosr.possystembackend.countersale.infrastructure.adapter.input.dto;

import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CounterSaleSunatCombinationCounterSaleResponse {
    private Long counterSaleId;
    private String sourceDocumentLabel;
    private String series;
    private Long number;
    private String status;
    private BigDecimal subtotal;
    private BigDecimal discountTotal;
    private BigDecimal igvAmount;
    private BigDecimal total;
    private Boolean associatedToSunat;
    private Long associatedSaleId;
    private String associatedDocType;
    private String associatedSeries;
    private Long associatedNumber;
    private LocalDateTime associatedAt;
}
