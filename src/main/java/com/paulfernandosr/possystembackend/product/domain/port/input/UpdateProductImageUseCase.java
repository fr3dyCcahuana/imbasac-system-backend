package com.paulfernandosr.possystembackend.product.domain.port.input;

import com.paulfernandosr.possystembackend.product.domain.ProductImage;

public interface UpdateProductImageUseCase {
    void updateProductImage(Long productId, Long imageId, ProductImage image);
}
