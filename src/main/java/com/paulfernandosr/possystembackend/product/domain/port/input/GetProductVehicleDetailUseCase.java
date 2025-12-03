package com.paulfernandosr.possystembackend.product.domain.port.input;

import com.paulfernandosr.possystembackend.product.domain.ProductVehicleDetail;

public interface GetProductVehicleDetailUseCase {
    ProductVehicleDetail getVehicleDetailByProductId(Long productId);
}
