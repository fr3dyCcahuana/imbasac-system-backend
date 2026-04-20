package com.paulfernandosr.possystembackend.product.domain;

import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductPurchasePrice {

    private Long purchaseItemId;
    private Long purchaseId;
    private Integer lineNumber;

    private LocalDate issueDate;
    private LocalDate entryDate;

    private String documentType;
    private String documentSeries;
    private String documentNumber;

    private String currency;
    private String paymentType;

    private String supplierRuc;
    private String supplierBusinessName;
    private String supplierAddress;

    private BigDecimal quantity;
    private BigDecimal unitCost;
    private BigDecimal discountPercent;
    private BigDecimal discountAmount;
    private BigDecimal igvRate;
    private BigDecimal igvAmount;
    private BigDecimal freightAllocated;
    private BigDecimal totalCost;

    private String lotCode;
    private LocalDate expirationDate;
    private LocalDateTime createdAt;
}
