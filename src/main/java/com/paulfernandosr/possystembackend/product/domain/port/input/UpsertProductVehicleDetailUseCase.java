package com.paulfernandosr.possystembackend.product.domain.port.input;

import com.paulfernandosr.possystembackend.product.domain.ProductVehicleDetail;

public interface UpsertProductVehicleDetailUseCase {
    void upsertVehicleDetail(Long productId, ProductVehicleDetail detail);
}
