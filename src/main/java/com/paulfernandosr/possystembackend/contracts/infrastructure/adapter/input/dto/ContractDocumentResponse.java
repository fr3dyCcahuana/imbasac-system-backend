package com.paulfernandosr.possystembackend.contracts.infrastructure.adapter.input.dto;

import lombok.*;

import java.time.LocalDate;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ContractDocumentResponse {
    private Long contractId;
    private String series;
    private Long number;
    private LocalDate issueDate;
    private String status;
}
