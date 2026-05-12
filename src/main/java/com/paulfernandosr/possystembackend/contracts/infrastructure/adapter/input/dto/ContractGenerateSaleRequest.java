package com.paulfernandosr.possystembackend.contracts.infrastructure.adapter.input.dto;

import com.paulfernandosr.possystembackend.salev2.domain.model.DocType;
import com.paulfernandosr.possystembackend.salev2.domain.model.PaymentMethod;
import com.paulfernandosr.possystembackend.salev2.domain.model.TaxStatus;
import lombok.*;

import java.time.LocalDate;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ContractGenerateSaleRequest {

    /**
     * Genera el comprobante en ventas SIN emitir SUNAT.
     * Para contratos de motocicleta se usará B004/F004 según docType.
     */
    private DocType docType;
    private LocalDate issueDate;
    private TaxStatus taxStatus;
    private Boolean igvIncluded;

    /** Solo aplica cuando el contrato es CONTADO. */
    private PaymentMethod paymentMethod;
}
