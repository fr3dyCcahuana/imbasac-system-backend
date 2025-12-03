package com.paulfernandosr.possystembackend.product.domain.port.input;

import com.paulfernandosr.possystembackend.product.domain.ProductImage;

import java.util.Collection;

public interface GetProductImagesUseCase {
    Collection<ProductImage> getImagesByProductId(Long productId);
}
