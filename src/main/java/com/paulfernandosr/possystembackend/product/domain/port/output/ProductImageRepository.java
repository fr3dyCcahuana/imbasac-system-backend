package com.paulfernandosr.possystembackend.product.domain.port.output;

import com.paulfernandosr.possystembackend.product.domain.ProductImage;

import java.util.Collection;
import java.util.Optional;

public interface ProductImageRepository {

    void create(ProductImage image);

    Collection<ProductImage> findByProductId(Long productId);

    /**
     * Recupera todas las imágenes asociadas a múltiples productos.
     * Útil para evitar N+1 en el paginado.
     */
    Collection<ProductImage> findByProductIds(Collection<Long> productIds);

    Optional<ProductImage> findByIdAndProductId(Long imageId, Long productId);

    void update(ProductImage image);

    void deleteByIdAndProductId(Long imageId, Long productId);
}
