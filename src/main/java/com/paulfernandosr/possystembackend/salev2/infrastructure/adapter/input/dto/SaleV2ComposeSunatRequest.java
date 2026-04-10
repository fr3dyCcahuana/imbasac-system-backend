package com.paulfernandosr.possystembackend.salev2.infrastructure.adapter.input.dto;

import lombok.*;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SaleV2ComposeSunatRequest {

    private String editReason;

    @Builder.Default
    private List<SaleItemAdjustment> saleItems = new ArrayList<>();

    @Builder.Default
    private List<CounterSaleSelection> counterSales = new ArrayList<>();

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class SaleItemAdjustment {
        private Long saleItemId;
        private BigDecimal unitPriceOverride;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class CounterSaleSelection {
        private Long counterSaleId;

        @Builder.Default
        private List<CounterSaleItemAdjustment> items = new ArrayList<>();
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class CounterSaleItemAdjustment {
        private Long counterSaleItemId;
        private BigDecimal unitPriceOverride;
    }
}
