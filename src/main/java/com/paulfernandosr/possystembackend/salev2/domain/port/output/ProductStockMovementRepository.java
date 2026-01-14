package com.paulfernandosr.possystembackend.salev2.domain.port.output;

import java.math.BigDecimal;

public interface ProductStockMovementRepository {
    void createOutSale(Long productId, BigDecimal quantityOut, Long saleItemId);

    /**
     * Movimiento de reversa por anulación/devolución de una venta.
     */
    void createInReturn(Long productId, BigDecimal quantityIn, Long saleItemId, BigDecimal unitCost, BigDecimal totalCost);
}
