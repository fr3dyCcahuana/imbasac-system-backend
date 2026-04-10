package com.paulfernandosr.possystembackend.countersale.domain.port.output;

import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public interface CounterSaleRepository {
    Long insertCounterSale(Long stationId,
                           Long saleSessionId,
                           Long createdBy,
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
                           BigDecimal igvRate,
                           Boolean igvIncluded,
                           String notes);

    Long insertCounterSaleItem(Long counterSaleId,
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
                               Boolean affectsStock,
                               BigDecimal unitCostSnapshot,
                               BigDecimal totalCostSnapshot,
                               BigDecimal revenueTotal);

    void linkSerialUnit(Long counterSaleItemId, Long serialUnitId);

    void updateTotals(Long counterSaleId,
                      BigDecimal subtotal,
                      BigDecimal discountTotal,
                      BigDecimal igvAmount,
                      BigDecimal total,
                      BigDecimal giftCostTotal);

    LockedCounterSale lockById(Long counterSaleId);

    List<CounterSaleItemForVoid> findItemsByCounterSaleId(Long counterSaleId);

    void markAsVoided(Long counterSaleId, Long voidedBy, String voidReason);

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    class LockedCounterSale {
        private Long id;
        private Long saleSessionId;
        private Long customerId;
        private String status;
        private BigDecimal total;
        private BigDecimal discountTotal;
        private Boolean associatedToSunat;
        private Long associatedSaleId;
        private String associatedDocType;
        private String associatedSeries;
        private Long associatedNumber;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    class CounterSaleItemForVoid {
        private Long id;
        private Integer lineNumber;
        private Long productId;
        private String sku;
        private String description;
        private String presentation;
        private BigDecimal factor;
        private BigDecimal quantity;
        private BigDecimal unitPrice;
        private BigDecimal discountPercent;
        private BigDecimal discountAmount;
        private String lineKind;
        private String giftReason;
        private Boolean affectsStock;
        private BigDecimal revenueTotal;
        private BigDecimal unitCostSnapshot;
        private BigDecimal totalCostSnapshot;
    }
}
