package com.paulfernandosr.possystembackend.product.domain.port.output;

import com.paulfernandosr.possystembackend.product.domain.ProductVehicleSpecs;

import java.util.Optional;

public interface ProductVehicleSpecsRepository {

    Optional<ProductVehicleSpecs> findByProductId(Long productId);

    boolean existsByProductId(Long productId);

    void create(ProductVehicleSpecs specs);

    void update(ProductVehicleSpecs specs);
}
