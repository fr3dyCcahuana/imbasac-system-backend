package com.paulfernandosr.possystembackend.salev2.domain.port.output;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import lombok.*;

public interface SaleV2Repository {
    Long insertSale(Long stationId,
                    Long saleSessionId,
                    Long createdBy,
                    String docType,
                    String series,
                    Long number,
                    LocalDate issueDate,
                    String currency,
                    BigDecimal exchangeRate,
                    String priceList,
                    Long customerId,
                    String customerDocType,
                    String customerDocNumber,
                    String customerName,
                    String customerAddress,
                    String taxStatus,
                    String taxReason,
                    BigDecimal igvRate,
                    String paymentType,
                    Integer creditDays,
                    LocalDate dueDate,
                    String notes);

    Long insertSaleItem(Long saleId,
                       Integer lineNumber,
                       Long productId,
                       String sku,
                       String description,
                       String presentation,
                       BigDecimal factor,
                       BigDecimal quantity,
                       BigDecimal unitPrice,
                       BigDecimal discountPercent,
                       BigDecimal discountAmount,
                       String lineKind,
                       String giftReason,
                       Boolean facturableSunat,
                       Boolean affectsStock,
                       Boolean visibleInDocument,
                       BigDecimal unitCostSnapshot,
                       BigDecimal totalCostSnapshot,
                       BigDecimal revenueTotal);

    void updateTotals(Long saleId,
                      BigDecimal subtotal,
                      BigDecimal discountTotal,
                      BigDecimal igvAmount,
                      BigDecimal total,
                      BigDecimal giftCostTotal);

    LockedSale lockById(Long saleId);
    List<SaleItemForVoid> findItemsBySaleId(Long saleId);
    void markAsVoided(Long saleId, String voidNote);

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    class LockedSale {
        private Long id;
        private Long saleSessionId;
        private Long customerId;
        private String docType;
        private String status;
        private String paymentType;
        private BigDecimal total;
        private BigDecimal discountTotal;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    class SaleItemForVoid {
        private Long id;
        private Long productId;
        private BigDecimal quantity;
        private Boolean affectsStock;
        private BigDecimal unitCostSnapshot;
        private BigDecimal totalCostSnapshot;
    }
}
