package com.paulfernandosr.possystembackend.product.domain.port.output;

import com.paulfernandosr.possystembackend.product.domain.ProductImage;

import java.util.Collection;
import java.util.Optional;

public interface ProductImageRepository {

    void create(ProductImage image);

    Collection<ProductImage> findByProductId(Long productId);

    Optional<ProductImage> findByIdAndProductId(Long imageId, Long productId);

    void update(ProductImage image);

    void deleteByIdAndProductId(Long imageId, Long productId);
}
