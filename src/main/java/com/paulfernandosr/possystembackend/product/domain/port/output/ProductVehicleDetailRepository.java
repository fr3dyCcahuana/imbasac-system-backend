package com.paulfernandosr.possystembackend.product.domain.port.output;

import com.paulfernandosr.possystembackend.product.domain.ProductVehicleDetail;

import java.util.Optional;

public interface ProductVehicleDetailRepository {

    Optional<ProductVehicleDetail> findByProductId(Long productId);

    boolean existsByProductId(Long productId);

    void create(ProductVehicleDetail detail);

    void update(ProductVehicleDetail detail);
}
