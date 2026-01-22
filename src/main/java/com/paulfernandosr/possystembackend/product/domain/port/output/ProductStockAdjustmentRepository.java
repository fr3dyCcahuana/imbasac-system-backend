package com.paulfernandosr.possystembackend.product.domain.port.output;

import com.paulfernandosr.possystembackend.product.domain.ProductStockAdjustment;

public interface ProductStockAdjustmentRepository {
    ProductStockAdjustment create(ProductStockAdjustment adjustment);
}
