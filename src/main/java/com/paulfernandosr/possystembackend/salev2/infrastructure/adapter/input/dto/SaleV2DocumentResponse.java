package com.paulfernandosr.possystembackend.salev2.infrastructure.adapter.input.dto;

import com.paulfernandosr.possystembackend.salev2.domain.model.DocType;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;

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
    private LocalDate issueDate;

    private BigDecimal subtotal;
    private BigDecimal discountTotal;
    private BigDecimal igvAmount;
    private BigDecimal total;
    private BigDecimal giftCostTotal;
}
