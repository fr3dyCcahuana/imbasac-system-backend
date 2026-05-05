package com.paulfernandosr.possystembackend.salev2.domain.port.output;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import lombok.*;

public interface SaleV2Repository {

    int updateSourceProformaId(Long saleId, Long proformaId);

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
                    Boolean igvIncluded,
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
    LockedEditableSale lockEditableById(Long saleId);
    List<SaleItemForVoid> findItemsBySaleId(Long saleId);
    void deleteItemsBySaleId(Long saleId);
    void updateHeaderForAdminEdit(Long saleId,
                                  LocalDate issueDate,
                                  String priceList,
                                  Long customerId,
                                  String customerDocType,
                                  String customerDocNumber,
                                  String customerName,
                                  String customerAddress,
                                  String taxStatus,
                                  String taxReason,
                                  BigDecimal igvRate,
                                  Boolean igvIncluded,
                                  Integer creditDays,
                                  LocalDate dueDate,
                                  BigDecimal subtotal,
                                  BigDecimal discountTotal,
                                  BigDecimal igvAmount,
                                  BigDecimal total,
                                  BigDecimal giftCostTotal,
                                  String notes,
                                  Long editedBy,
                                  String editReason);

    void insertEditHistory(Long saleId,
                           String editReason,
                           Long editedBy,
                           String editedByUsername,
                           String beforeSnapshotJson,
                           String afterSnapshotJson);

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
    class LockedEditableSale {
        private Long id;
        private Long customerId;
        private String docType;
        private String series;
        private Long number;
        private LocalDate issueDate;
        private String currency;
        private BigDecimal exchangeRate;
        private String priceList;
        private String customerDocType;
        private String customerDocNumber;
        private String customerName;
        private String customerAddress;
        private String taxStatus;
        private String taxReason;
        private BigDecimal igvRate;
        private Boolean igvIncluded;
        private String paymentType;
        private Integer creditDays;
        private LocalDate dueDate;
        private String notes;
        private String status;
        private String sunatStatus;
        private String editStatus;
        private Integer editCount;
        private java.time.LocalDateTime lastEditedAt;
        private Long lastEditedBy;
        private String lastEditReason;
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
        private Boolean facturableSunat;
        private Boolean affectsStock;
        private Boolean visibleInDocument;
        private BigDecimal revenueTotal;
        private BigDecimal unitCostSnapshot;
        private BigDecimal totalCostSnapshot;
    }
}
