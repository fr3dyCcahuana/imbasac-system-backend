package com.paulfernandosr.possystembackend.countersale.domain.port.output;

import java.math.BigDecimal;

public interface ProductStockMovementRepository {
    void createOutCounterSale(Long productId, BigDecimal quantityOut, Long counterSaleItemId);
    void createInCounterSaleVoid(Long productId, BigDecimal quantityIn, Long counterSaleItemId, BigDecimal unitCost, BigDecimal totalCost);
}
