package com.paulfernandosr.possystembackend.salev2.domain.port.output;

import java.math.BigDecimal;

public interface ProductStockMovementRepository {

    void createOutSaleMovement(Long productId, BigDecimal quantityOut, Long saleItemId);
}
