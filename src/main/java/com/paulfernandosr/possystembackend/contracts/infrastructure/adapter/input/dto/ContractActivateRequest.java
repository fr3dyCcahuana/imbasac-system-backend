package com.paulfernandosr.possystembackend.contracts.infrastructure.adapter.input.dto;

import com.paulfernandosr.possystembackend.salev2.domain.model.PaymentMethod;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ContractActivateRequest {
    /** Obligatorio cuando el contrato es CREDITO y initialAmount > 0. */
    private PaymentMethod initialPaymentMethod;

    /** Si TRUE, la unidad pasa a ENTREGADO_CREDITO. Si FALSE/null, queda RESERVADO. */
    private Boolean deliveredToCustomer;

    private String note;
}
