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
    private int receivedLinesCount;
    private int extractedCodesCount;
    private int processedCodesCount;
    private boolean limitedByMax;
    private int maxCodesAllowed;

    /** Productos disponibles para cotizar o agregados al confirmar. */
    @Builder.Default
    private List<AddedItem> addedItems = new ArrayList<>();

    /** Códigos exactos encontrados, pero sin stock suficiente para cotizar. */
    @Builder.Default
    private List<String> noStockCodes = new ArrayList<>();

    /** Códigos exactos encontrados con stock menor a lo solicitado. */
    @Builder.Default
    private List<String> insufficientStockCodes = new ArrayList<>();

    /** Códigos que no existen en sku/factory_code/barcode. */
    @Builder.Default
    private List<String> notFoundCodes = new ArrayList<>();

    /** Líneas ignoradas por formato, si aplica. */
    @Builder.Default
    private List<String> ignoredLines = new ArrayList<>();

    private BigDecimal total;

    public boolean hasAddedItems() {
        return addedItems != null && !addedItems.isEmpty();
    }

    public int unavailableCount() {
        int a = noStockCodes == null ? 0 : noStockCodes.size();
        int b = insufficientStockCodes == null ? 0 : insufficientStockCodes.size();
        int c = notFoundCodes == null ? 0 : notFoundCodes.size();
        return a + b + c;
    }

    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AddedItem {
        private Long productId;
        private String code;
        private String name;
        private BigDecimal quantity;
        private BigDecimal unitPrice;
        private BigDecimal lineTotal;
        private BigDecimal stock;
        private String priceList;
    }
}
