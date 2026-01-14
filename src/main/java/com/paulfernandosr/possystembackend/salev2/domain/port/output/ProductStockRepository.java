package com.paulfernandosr.possystembackend.salev2.domain.port.output;

import java.math.BigDecimal;

public interface ProductStockRepository {
    BigDecimal getOnHand(Long productId);
    BigDecimal getAverageCost(Long productId);
    BigDecimal getLastUnitCost(Long productId);
    void decreaseOnHandOrFail(Long productId, BigDecimal quantity);

    /**
     * Reversa de stock (anulación/devolución). Si el registro no existe, lo crea en 0 + qty.
     */
    void increaseOnHand(Long productId, BigDecimal quantity);
}
