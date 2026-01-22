package com.paulfernandosr.possystembackend.product.domain;

import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductStockAdjustmentResult {

    private Long adjustmentId;
    private Long productId;

    private String movementType;

    private BigDecimal quantity;
    private BigDecimal unitCost;
    private BigDecimal totalCost;

    private BigDecimal balanceQty;
    private BigDecimal balanceCost;

    private LocalDateTime createdAt;
}
