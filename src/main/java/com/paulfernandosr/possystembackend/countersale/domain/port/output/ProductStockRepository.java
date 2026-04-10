package com.paulfernandosr.possystembackend.countersale.domain.port.output;

import java.math.BigDecimal;

public interface ProductStockRepository {
    BigDecimal getOnHand(Long productId);
    BigDecimal getAverageCost(Long productId);
    BigDecimal getLastUnitCost(Long productId);
    void decreaseOnHandOrFail(Long productId, BigDecimal quantity);
    void increaseOnHand(Long productId, BigDecimal quantity);
}
