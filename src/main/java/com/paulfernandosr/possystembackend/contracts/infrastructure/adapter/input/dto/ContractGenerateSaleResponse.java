package com.paulfernandosr.possystembackend.contracts.infrastructure.adapter.input.dto;

import lombok.*;

import java.time.LocalDate;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ContractGenerateSaleResponse {
    private Long contractId;
    private Long saleId;
    private String saleSeries;
    private Long saleNumber;
    private String saleDocType;
    private LocalDate issueDate;
}
