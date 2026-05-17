package com.paulfernandosr.possystembackend.whatsapp.domain;

import lombok.*;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WhatsAppBatchQuoteResult {
    @Builder.Default
    private List<AddedItem> addedItems = new ArrayList<>();
    @Builder.Default
    private List<String> notFoundCodes = new ArrayList<>();
    @Builder.Default
    private List<String> noStockCodes = new ArrayList<>();
    @Builder.Default
    private List<String> insufficientStockCodes = new ArrayList<>();
    @Builder.Default
    private List<String> ignoredLines = new ArrayList<>();
    private BigDecimal total;

    public boolean hasAddedItems() {
        return addedItems != null && !addedItems.isEmpty();
    }

    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AddedItem {
        private String code;
        private String name;
        private BigDecimal quantity;
        private BigDecimal unitPrice;
        private BigDecimal lineTotal;
        private BigDecimal stock;
    }
}
