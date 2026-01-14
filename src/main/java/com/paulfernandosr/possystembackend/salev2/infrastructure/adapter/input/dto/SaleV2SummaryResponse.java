package com.paulfernandosr.possystembackend.salev2.infrastructure.adapter.input.dto;

import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SaleV2SummaryResponse {
    private Long saleId;
    private String docType;
    private String series;
    private Long number;
    private LocalDate issueDate;

    private String customerDocNumber;
    private String customerName;

    private String paymentType;
    private BigDecimal total;
    private String status;
}
