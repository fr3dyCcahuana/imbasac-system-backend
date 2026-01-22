package com.paulfernandosr.possystembackend.product.domain;

import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductStockAdjustment {

    private Long id;
    private Long productId;

    private String movementType; // IN_ADJUST / OUT_ADJUST

    private BigDecimal quantity;
    private BigDecimal unitCost;   // obligatorio en IN_ADJUST
    private BigDecimal totalCost;

    private String reason;
    private String note;

    private Long createdBy;
    private LocalDateTime createdAt;
}
