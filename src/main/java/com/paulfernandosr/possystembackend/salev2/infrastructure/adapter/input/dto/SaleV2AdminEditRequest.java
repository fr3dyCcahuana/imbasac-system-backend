package com.paulfernandosr.possystembackend.salev2.infrastructure.adapter.input.dto;

import com.paulfernandosr.possystembackend.salev2.domain.model.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SaleV2AdminEditRequest {

    private LocalDate issueDate;

    private PriceList priceList;

    private Long customerId;
    private String customerDocType;
    private String customerDocNumber;
    private String customerName;
    private String customerAddress;

    private TaxStatus taxStatus;
    private String taxReason;
    private BigDecimal igvRate;
    private Boolean igvIncluded;

    private Integer creditDays;
    private LocalDate dueDate;

    private String notes;

    private List<Item> items;
    private Payment payment;

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class Item {
        private Long productId;
        private BigDecimal quantity;
        private BigDecimal discountPercent;
        private LineKind lineKind;
        private String giftReason;
        private BigDecimal unitPriceOverride;
        private List<Long> serialUnitIds;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class Payment {
        private PaymentMethod method;
    }
}
