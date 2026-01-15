package com.paulfernandosr.possystembackend.product.domain.port.input;

import com.paulfernandosr.possystembackend.product.domain.ProductSerialUnit;

public interface CreateProductSerialUnitUseCase {
    ProductSerialUnit create(Long productId, ProductSerialUnit unit);
}
