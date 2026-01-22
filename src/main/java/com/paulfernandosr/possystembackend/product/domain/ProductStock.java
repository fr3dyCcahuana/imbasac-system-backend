package com.paulfernandosr.possystembackend.product.domain;

import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductStock {

    private Long productId;

    private BigDecimal quantityOnHand;
    private BigDecimal averageCost;
    private BigDecimal lastUnitCost;

    private LocalDateTime lastMovementAt;
}
