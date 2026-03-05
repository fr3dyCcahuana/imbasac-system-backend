package com.paulfernandosr.possystembackend.contracts.infrastructure.adapter.input.dto;

import com.paulfernandosr.possystembackend.salev2.domain.model.DocType;
import com.paulfernandosr.possystembackend.salev2.domain.model.PaymentMethod;
import com.paulfernandosr.possystembackend.salev2.domain.model.TaxStatus;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ContractGenerateSaleRequest {

    private DocType docType;
    private String series;
    private LocalDate issueDate;

    private TaxStatus taxStatus;
    private Boolean igvIncluded;
    private BigDecimal igvRate;

    private PaymentMethod paymentMethod;

    private Boolean useTotalAmountAsUnitPrice;
}
