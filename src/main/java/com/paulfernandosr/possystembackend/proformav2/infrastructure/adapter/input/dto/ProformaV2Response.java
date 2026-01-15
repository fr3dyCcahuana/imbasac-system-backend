package com.paulfernandosr.possystembackend.proformav2.infrastructure.adapter.input.dto;

import lombok.*;

import java.math.BigDecimal;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProformaV2Response {
    private Long id;
    private Long stationId;
    private Long createdBy;
    private String createdByUsername;
    private String createdByFirstName;
    private String createdByLastName;
    private String series;
    private Long number;
    private String issueDate;

    private String taxStatus;
    private BigDecimal igvRate;
    private Boolean igvIncluded;
    private BigDecimal igvAmount;

    private Character priceList;
    private String currency;

    private Long customerId;
    private String customerDocType;
    private String customerDocNumber;
    private String customerName;
    private String customerAddress;

    private String notes;

    private BigDecimal subtotal;
    private BigDecimal discountTotal;
    private BigDecimal total;

    private String status;

    private List<Item> items;

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class Item {
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
        private BigDecimal lineSubtotal;

        private Boolean facturableSunat;
        private Boolean affectsStock;
    }
}
