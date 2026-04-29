package com.paulfernandosr.possystembackend.countersale.domain.port.output;

import java.math.BigDecimal;

public interface ProductStockMovementRepository {
    void createOutCounterSale(Long productId,
                              BigDecimal quantityOut,
                              Long counterSaleItemId,
                              BigDecimal unitCost,
                              BigDecimal totalCost,
                              BigDecimal balanceQty,
                              BigDecimal balanceCost);

    void createInCounterSaleVoid(Long productId,
                                 BigDecimal quantityIn,
                                 Long counterSaleItemId,
                                 BigDecimal unitCost,
                                 BigDecimal totalCost,
                                 BigDecimal balanceQty,
                                 BigDecimal balanceCost);
}
