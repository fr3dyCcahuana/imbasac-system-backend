package com.paulfernandosr.possystembackend.proformav2.infrastructure.adapter.input.dto;

import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProformaV2SummaryResponse {
    private Long proformaId;
    private String docType;
    private String series;
    private Long number;
    private LocalDate issueDate;

    private String customerDocNumber;
    private String customerName;

    private String paymentType;
    private Integer creditDays;
    private LocalDate dueDate;

    private BigDecimal total;
    private String status;
    private Long convertedSaleId;
}
