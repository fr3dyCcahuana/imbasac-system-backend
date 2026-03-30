package com.paulfernandosr.possystembackend.purchase.domain;

import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PurchaseProductStock {
    private BigDecimal quantityOnHand;
    private BigDecimal averageCost;
    private BigDecimal lastUnitCost;
    private LocalDateTime lastMovementAt;
}
