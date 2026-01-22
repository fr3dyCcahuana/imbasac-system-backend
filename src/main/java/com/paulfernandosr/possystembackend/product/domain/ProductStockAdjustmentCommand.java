package com.paulfernandosr.possystembackend.product.domain;

import lombok.*;

import java.math.BigDecimal;
import java.util.List;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductStockAdjustmentCommand {

    private String movementType; // IN_ADJUST / OUT_ADJUST
    private BigDecimal quantity;
    private BigDecimal unitCost; // obligatorio en IN_ADJUST

    private String reason;
    private String note;
    private String locationCode;

    // Si el producto es serializado, se deben enviar unidades.
    private List<ProductSerialUnit> serialUnits;
}
