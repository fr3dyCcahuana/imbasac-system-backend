package com.paulfernandosr.possystembackend.product.domain.port.input;

import com.paulfernandosr.possystembackend.product.domain.Product;

public interface UpdateProductInfoUseCase {
    void updateProductInfoById(Long productId, Product product);
}
