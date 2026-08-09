package com.paulfernandosr.possystembackend.salev2.domain.port.output;

import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public interface SaleCreditNoteRepository {

    LockedSaleForCreditNote lockSale(Long saleId);

    List<SaleItemForCreditNote> findSaleItems(Long saleId);

    Map<Long, BigDecimal> findCreditedQuantities(Long saleId);

    Long insertCreditNote(CreditNoteRecord record);

    Long insertCreditNoteItem(CreditNoteItemRecord record);

    void updateEmissionResult(Long creditNoteId,
                              String sunatStatus,
                              String sunatCode,
                              String sunatDescription,
                              String hashCode,
                              String xmlPath,
                              String cdrPath,
                              String pdfPath,
                              LocalDateTime emittedAt);

    void markAsVoided(Long creditNoteId, String reason);

    List<CreditNoteView> findCreditNotesBySaleId(Long saleId);

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    class CreditNoteView {
        private Long creditNoteId;
        private Long saleId;
        private String docType;
        private String series;
        private Long number;
        private LocalDate issueDate;
        private String creditNoteTypeCode;
        private String creditNoteTypeDescription;
        private String reason;
        private Boolean returnedToStock;
        private BigDecimal subtotal;
        private BigDecimal igvAmount;
        private BigDecimal total;
        private String status;
        private String sunatStatus;
        private String sunatCode;
        private String sunatDescription;
        private String hashCode;
        private String xmlPath;
        private String cdrPath;
        private String pdfPath;
        private LocalDateTime emittedAt;
        private List<CreditNoteItemView> items;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    class CreditNoteItemView {
        private Long creditNoteItemId;
        private Long creditNoteId;
        private Long saleItemId;
        private Long productId;
        private String sku;
        private String description;
        private BigDecimal quantity;
        private BigDecimal revenueTotal;
        private Boolean returnedToStock;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    class LockedSaleForCreditNote {
        private Long saleId;
        private Long customerId;
        private String status;
        private String docType;
        private String series;
        private Long number;
        private LocalDate issueDate;
        private LocalDateTime createdAt;
        private String currency;
        private String customerDocType;
        private String customerDocNumber;
        private String customerName;
        private String customerAddress;
        private String taxStatus;
        private BigDecimal igvRate;
        private Boolean igvIncluded;
        private BigDecimal subtotal;
        private BigDecimal igvAmount;
        private BigDecimal total;
        private String sunatStatus;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    class SaleItemForCreditNote {
        private Long saleItemId;
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
        private Boolean facturableSunat;
        private Boolean affectsStock;
        private Boolean visibleInDocument;
        private BigDecimal unitCostSnapshot;
        private BigDecimal totalCostSnapshot;
        private BigDecimal revenueTotal;
        private String productCategory;
        private String sunatProductCode;
        private Long serialUnitId;
        private Long counterSaleItemId;
        private Boolean counterSaleItemAffectsStock;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    class CreditNoteRecord {
        private Long saleId;
        private Long createdBy;
        private String docType;
        private String series;
        private Long number;
        private LocalDate issueDate;
        private String currency;
        private String customerDocType;
        private String customerDocNumber;
        private String customerName;
        private String customerAddress;
        private String taxStatus;
        private BigDecimal igvRate;
        private Boolean igvIncluded;
        private String creditNoteTypeCode;
        private String creditNoteTypeDescription;
        private String reason;
        private Boolean returnedToStock;
        private BigDecimal subtotal;
        private BigDecimal igvAmount;
        private BigDecimal total;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    class CreditNoteItemRecord {
        private Long creditNoteId;
        private Long saleItemId;
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
        private Boolean facturableSunat;
        private Boolean affectsStock;
        private Boolean visibleInDocument;
        private BigDecimal unitCostSnapshot;
        private BigDecimal totalCostSnapshot;
        private BigDecimal revenueTotal;
        private Boolean returnedToStock;
    }
}
