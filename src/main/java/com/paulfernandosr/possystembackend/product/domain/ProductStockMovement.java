package com.paulfernandosr.possystembackend.product.domain;

import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductStockMovement {

    private Long id;

    private Long productId;
    private String movementType;   // IN_PURCHASE, OUT_SALE, IN_ADJUST, OUT_ADJUST, etc.

    private String sourceTable;    // purchase_item, sale_item, product_stock_adjustment, ...
    private Long sourceId;

    private BigDecimal quantityIn;
    private BigDecimal quantityOut;

    private BigDecimal unitCost;
    private BigDecimal totalCost;

    private BigDecimal balanceQty;
    private BigDecimal balanceCost;

    private LocalDateTime createdAt;
}
