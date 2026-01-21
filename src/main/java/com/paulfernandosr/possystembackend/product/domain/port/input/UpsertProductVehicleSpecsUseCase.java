package com.paulfernandosr.possystembackend.product.domain.port.input;

import com.paulfernandosr.possystembackend.product.domain.ProductVehicleSpecs;

public interface UpsertProductVehicleSpecsUseCase {
    void upsert(Long productId, ProductVehicleSpecs specs);
}
