package com.paulfernandosr.possystembackend.countersale.domain.port.output;

import com.paulfernandosr.possystembackend.countersale.domain.model.StockMovementBalance;

import java.math.BigDecimal;

public interface ProductStockRepository {
    BigDecimal getOnHand(Long productId);
    BigDecimal getAverageCost(Long productId);
    BigDecimal getLastUnitCost(Long productId);

    /**
     * Descuenta stock de forma atómica y retorna el saldo posterior.
     */
    StockMovementBalance decreaseOnHandOrFail(Long productId, BigDecimal quantity);

    /**
     * Incrementa stock de forma atómica y retorna el saldo posterior.
     */
    StockMovementBalance increaseOnHand(Long productId, BigDecimal quantity);
}
