package com.paulfernandosr.possystembackend.contracts.infrastructure.adapter.input.dto;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ContractRepossessionRequest {
    private String reason;

    /** PENALIDAD / DEVOLUCION / TRANSFERENCIA / OTRO */
    private String paidAmountTreatment;

    private String note;
}
