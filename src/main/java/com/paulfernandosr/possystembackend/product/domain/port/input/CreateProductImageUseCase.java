package com.paulfernandosr.possystembackend.product.domain.port.input;

import com.paulfernandosr.possystembackend.product.domain.ProductImage;

public interface CreateProductImageUseCase {
    void createProductImage(Long productId, ProductImage image);
}
