package com.paulfernandosr.possystembackend.salev2.infrastructure.adapter.input.dto;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SaleV2RelationInfoResponse {

    /**
     * NORMAL
     * PROFORMA
     * CONTRACT
     * COUNTER_SALE_DAILY
     * COUNTER_SALE_COMPOSITION
     */
    private String relationType;
    private String relationLabel;

    private Long proformaId;
    private String proformaSeries;
    private Long proformaNumber;

    private Long contractId;
    private String contractSeries;
    private Long contractNumber;

    private Long counterSaleComboId;
    private String counterSaleComboStatus;
    private String counterSaleDocumentsLabel;

    private Integer counterSaleCount;
    private Integer pendingCounterSaleCount;
    private Integer acceptedCounterSaleCount;

    private Boolean hasCounterSaleRelation;
    private Boolean hasPendingCounterSaleRelation;
}