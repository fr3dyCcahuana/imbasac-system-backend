package com.paulfernandosr.possystembackend.product.domain;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record ProductKardexTrendMovement(
        Long id,
        LocalDateTime movementDate,
        String movementType,
        String sourceTable,
        BigDecimal quantityIn,
        BigDecimal quantityOut,
        BigDecimal stockBefore,
        BigDecimal stockAfter,
        BigDecimal unitCost,
        BigDecimal totalCost,
        BigDecimal averageCostAfter,
        BigDecimal sourceLineTotal
) {
}
