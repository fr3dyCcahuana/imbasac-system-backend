package com.paulfernandosr.possystembackend.countersale.infrastructure.adapter.input.dto;

import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CounterSaleSunatCombinationRequest {
    private String customerMode;
    private List<Long> additionalCounterSaleIds;
    private List<LineOverride> lineOverrides;
    private String series;
    private LocalDate issueDate;
    private String paymentMethod;
    private String notes;

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class LineOverride {
        private Long counterSaleItemId;
        private BigDecimal emittedUnitPrice;
    }
}
