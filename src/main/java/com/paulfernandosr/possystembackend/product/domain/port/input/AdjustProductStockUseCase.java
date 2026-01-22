package com.paulfernandosr.possystembackend.product.domain.port.input;

import com.paulfernandosr.possystembackend.product.domain.ProductStockAdjustmentCommand;
import com.paulfernandosr.possystembackend.product.domain.ProductStockAdjustmentResult;

public interface AdjustProductStockUseCase {

    ProductStockAdjustmentResult adjust(Long productId, ProductStockAdjustmentCommand command);
}
