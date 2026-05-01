package com.paulfernandosr.possystembackend.product.domain.port.output;

import com.paulfernandosr.possystembackend.product.domain.ProductKardexTrendFilter;
import com.paulfernandosr.possystembackend.product.domain.ProductKardexTrendMovement;
import com.paulfernandosr.possystembackend.product.domain.ProductKardexTrendProductResponse;
import com.paulfernandosr.possystembackend.product.domain.ProductKardexTrendStockSnapshot;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface ProductKardexTrendRepository {

    Optional<ProductKardexTrendProductResponse> findProductById(Long productId);

    Optional<ProductKardexTrendStockSnapshot> findLastStockSnapshotBefore(
            Long productId,
            LocalDateTime before
    );

    List<ProductKardexTrendMovement> findMovements(ProductKardexTrendFilter filter);
}
