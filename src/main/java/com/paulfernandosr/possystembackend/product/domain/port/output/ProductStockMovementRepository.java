package com.paulfernandosr.possystembackend.product.domain.port.output;

import com.paulfernandosr.possystembackend.product.domain.ProductStockMovement;

public interface ProductStockMovementRepository {
    ProductStockMovement create(ProductStockMovement movement);
}
