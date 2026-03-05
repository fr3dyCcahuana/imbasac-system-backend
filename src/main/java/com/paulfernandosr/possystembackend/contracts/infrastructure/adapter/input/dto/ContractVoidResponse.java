package com.paulfernandosr.possystembackend.contracts.infrastructure.adapter.input.dto;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ContractVoidResponse {
    private Long contractId;
    private String previousStatus;
    private String status;
    private String message;
}
