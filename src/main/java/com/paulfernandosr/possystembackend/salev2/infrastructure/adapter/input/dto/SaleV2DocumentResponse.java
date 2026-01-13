package com.paulfernandosr.possystembackend.salev2.infrastructure.adapter.input.dto;

import com.paulfernandosr.possystembackend.salev2.domain.model.DocType;
import lombok.*;

import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SaleV2DocumentResponse {
    private Long saleId;
    private DocType docType;
    private String series;
    private Long number;
    private String currency;
    private BigDecimal total;
}
