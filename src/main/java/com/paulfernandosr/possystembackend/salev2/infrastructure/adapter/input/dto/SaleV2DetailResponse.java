package com.paulfernandosr.possystembackend.salev2.infrastructure.adapter.input.dto;

import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SaleV2DetailResponse {

    private Long saleId;

    private Long stationId;
    private Long saleSessionId;
    private Long createdBy;

    private String docType;
    private String series;
    private Long number;
    private LocalDate issueDate;

    private String currency;
    private BigDecimal exchangeRate;

    private String priceList;

    private Long customerId;
    private String customerDocType;
    private String customerDocNumber;
    private String customerName;
    private String customerAddress;

    private String taxStatus;
    private String taxReason;
    private BigDecimal igvRate;

    private String paymentType;
    private Integer creditDays;
    private LocalDate dueDate;

    private BigDecimal subtotal;
    private BigDecimal discountTotal;
    private BigDecimal igvAmount;
    private BigDecimal total;

    private BigDecimal giftCostTotal;

    private String notes;
    private String status;

    private List<SaleV2ItemResponse> items;

    private SaleV2PaymentResponse payment;

    private AccountsReceivableInfoResponse receivable;
    private List<AccountsReceivablePaymentInfo> receivablePayments;
}
