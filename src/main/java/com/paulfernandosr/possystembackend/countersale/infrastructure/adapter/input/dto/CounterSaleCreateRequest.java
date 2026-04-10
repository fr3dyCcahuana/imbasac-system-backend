package com.paulfernandosr.possystembackend.countersale.infrastructure.adapter.input.dto;

import com.paulfernandosr.possystembackend.countersale.domain.model.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CounterSaleCreateRequest {

    private Long stationId;
    private Long saleSessionId;

    private String series;
    private LocalDate issueDate;

    private String currency;
    private BigDecimal exchangeRate;

    private CounterSalePriceList priceList;

    private Long customerId;
    private String customerDocType;
    private String customerDocNumber;
    private String customerName;
    private String customerAddress;

    private CounterSaleTaxStatus taxStatus;
    private BigDecimal igvRate;
    private Boolean igvIncluded;

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
        private CounterSaleLineKind lineKind;
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
        private CounterSalePaymentMethod method;
    }
}
