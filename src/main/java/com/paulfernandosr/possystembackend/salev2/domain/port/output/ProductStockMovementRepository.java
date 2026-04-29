package com.paulfernandosr.possystembackend.salev2.domain.port.output;

import java.math.BigDecimal;

public interface ProductStockMovementRepository {
    void createOutSale(Long productId,
                       BigDecimal quantityOut,
                       Long saleItemId,
                       BigDecimal unitCost,
                       BigDecimal totalCost,
                       BigDecimal balanceQty,
                       BigDecimal balanceCost);

    /**
     * Movimiento de salida por edición administrativa de una venta antes de SUNAT.
     */
    void createOutEdit(Long productId,
                       BigDecimal quantityOut,
                       Long saleItemId,
                       BigDecimal unitCost,
                       BigDecimal totalCost,
                       BigDecimal balanceQty,
                       BigDecimal balanceCost);

    /**
     * Movimiento de reversa por anulación/devolución de una venta.
     */
    void createInReturn(Long productId,
                        BigDecimal quantityIn,
                        Long saleItemId,
                        BigDecimal unitCost,
                        BigDecimal totalCost,
                        BigDecimal balanceQty,
                        BigDecimal balanceCost);

    /**
     * Movimiento de ingreso por edición administrativa de una venta antes de SUNAT.
     */
    void createInEdit(Long productId,
                      BigDecimal quantityIn,
                      Long saleItemId,
                      BigDecimal unitCost,
                      BigDecimal totalCost,
                      BigDecimal balanceQty,
                      BigDecimal balanceCost);
}
