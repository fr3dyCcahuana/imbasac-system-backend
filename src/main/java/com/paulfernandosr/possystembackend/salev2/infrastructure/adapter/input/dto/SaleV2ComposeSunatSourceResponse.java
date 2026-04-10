package com.paulfernandosr.possystembackend.salev2.infrastructure.adapter.input.dto;

import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SaleV2ComposeSunatSourceResponse {
    private Long counterSaleId;
    private String series;
    private Long number;
    private BigDecimal total;
    private BigDecimal discountTotal;
    private String status;
    private Boolean associatedToSunat;
    private String associatedDocType;
    private String associatedSeries;
    private Long associatedNumber;
    private LocalDateTime associatedAt;
}
