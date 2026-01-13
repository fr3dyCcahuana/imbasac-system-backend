package com.paulfernandosr.possystembackend.proformav2.domain;

import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProformaItem {
    private Long id;
    private Long proformaId;
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

    private LocalDateTime createdAt;
}
