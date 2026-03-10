package com.paulfernandosr.possystembackend.contracts.infrastructure.adapter.input.dto;

import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ContractSummaryResponse {

    private Long contractId;

    private String series;
    private Long number;
    private LocalDate issueDate;

    private String customerDocNumber;
    private String customerName;

    private String paymentType;

    private Integer installments;
    private BigDecimal initialAmount;
    private BigDecimal totalAmount;

    private String status;

    private String sku;
    private String description;
    private String vin;

    private Long saleId;
    private String saleDocType;
    private String saleSeries;
    private Long saleNumber;
}
