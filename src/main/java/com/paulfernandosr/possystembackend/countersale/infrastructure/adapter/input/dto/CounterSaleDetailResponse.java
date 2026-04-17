package com.paulfernandosr.possystembackend.countersale.infrastructure.adapter.input.dto;

import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CounterSaleDetailResponse {
    private Long counterSaleId;
    private Long stationId;
    private Long saleSessionId;
    private Long createdBy;
    private String createdByUsername;
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
    private BigDecimal igvRate;
    private Boolean igvIncluded;
    private BigDecimal subtotal;
    private BigDecimal discountTotal;
    private BigDecimal igvAmount;
    private BigDecimal total;
    private BigDecimal giftCostTotal;
    private String notes;
    private String status;
    private Boolean associatedToSunat;
    private Long associatedSaleId;
    private String associatedDocType;
    private String associatedSeries;
    private Long associatedNumber;
    private LocalDateTime associatedAt;
    private Boolean canVoid;
    private CounterSaleVoidInfoResponse voidInfo;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Long associatedComboId;
    private List<CounterSaleItemResponse> items;
    private CounterSalePaymentResponse payment;
}
