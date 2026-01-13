package com.paulfernandosr.possystembackend.stock.domain;

import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StockMovement {

    private Long id;
    private Long productId;

    private String movementType;   // IN_PURCHASE, OUT_SALE, OUT_CANCEL_PURCHASE, etc.
    private String sourceTable;    // purchase_item, sale_item, etc.
    private Long sourceId;

    private BigDecimal quantityIn;
    private BigDecimal quantityOut;

    private BigDecimal unitCost;
    private BigDecimal totalCost;

    private BigDecimal balanceQty;
    private BigDecimal balanceCost;

    private LocalDateTime createdAt;
}
