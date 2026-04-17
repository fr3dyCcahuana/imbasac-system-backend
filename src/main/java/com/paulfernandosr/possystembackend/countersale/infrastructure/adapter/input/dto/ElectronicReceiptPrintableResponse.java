package com.paulfernandosr.possystembackend.countersale.infrastructure.adapter.input.dto;

import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ElectronicReceiptPrintableResponse {
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
    private String paymentType;
    private LocalDate dueDate;

    private BigDecimal subtotal;
    private BigDecimal discountTotal;
    private BigDecimal igvAmount;
    private BigDecimal total;

    private ElectronicReceiptPrintablePaymentResponse payment;
    private List<ElectronicReceiptPrintableItemResponse> items;
}
