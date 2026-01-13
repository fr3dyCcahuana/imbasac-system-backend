package com.paulfernandosr.possystembackend.stock.domain;

import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Stock {

    private Long productId;
    private BigDecimal quantityOnHand;
    private BigDecimal averageCost;
    private BigDecimal lastUnitCost;
    private LocalDateTime lastMovementAt;
}
