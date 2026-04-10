package com.paulfernandosr.possystembackend.countersale.infrastructure.adapter.input.dto;

import com.paulfernandosr.possystembackend.countersale.domain.model.CounterSalePaymentMethod;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CounterSaleDocumentResponse {
    private Long counterSaleId;
    private String series;
    private Long number;
    private LocalDate issueDate;
    private BigDecimal subtotal;
    private BigDecimal discountTotal;
    private BigDecimal igvAmount;
    private BigDecimal total;
    private BigDecimal giftCostTotal;
    private CounterSalePaymentMethod paymentMethod;
}
