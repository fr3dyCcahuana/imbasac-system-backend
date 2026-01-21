package com.paulfernandosr.possystembackend.product.domain.port.input;

import com.paulfernandosr.possystembackend.product.domain.ProductVehicleSpecs;

public interface GetProductVehicleSpecsUseCase {
    ProductVehicleSpecs getByProductId(Long productId);
}
