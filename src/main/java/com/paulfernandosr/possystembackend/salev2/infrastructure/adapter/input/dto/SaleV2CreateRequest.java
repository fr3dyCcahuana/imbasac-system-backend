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
public class SaleV2CreateRequest {

    private Long stationId;
    private DocType docType;
    private String series;
    private PriceList priceList;

    private LocalDate issueDate;
    private String currency;
    private BigDecimal exchangeRate;

    private TaxStatus taxStatus;
    private BigDecimal igvRate;

    private PaymentType paymentType;

    private Long customerId;
    private String customerDocType;
    private String customerDocNumber;
    private String customerName;
    private String customerAddress;

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
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class Payment {
        private String method; // EFECTIVO/YAPE/TRANSFERENCIA/OTRO
    }
}
