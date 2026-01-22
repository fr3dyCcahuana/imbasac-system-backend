package com.paulfernandosr.possystembackend.product.domain.port.output;

import com.paulfernandosr.possystembackend.product.domain.ProductStock;

import java.util.Optional;

public interface ProductStockRepository {

    Optional<ProductStock> findByProductIdForUpdate(Long productId);

    void upsert(ProductStock stock);
}
