package com.paulfernandosr.possystembackend.purchase.domain;

import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PurchaseItem {

    private Long id;
    private Long purchaseId;

    private Integer lineNumber;

    private Long productId;
    private String description;
    private String presentation;

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
    private List<PurchaseSerialUnit> serialUnits;
}
