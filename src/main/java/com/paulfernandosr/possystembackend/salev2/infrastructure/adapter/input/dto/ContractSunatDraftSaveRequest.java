package com.paulfernandosr.possystembackend.salev2.infrastructure.adapter.input.dto;

import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ContractSunatDraftSaveRequest {

    private String docType;
    private String series;
    private LocalDate issueDate;

    private String taxStatus;
    private String taxReason;
    private BigDecimal igvRate;
    private Boolean igvIncluded;

    private String editReason;

    private List<Item> items;

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class Item {
        private Long saleItemId;
        private BigDecimal sunatUnitPrice;
    }
}
