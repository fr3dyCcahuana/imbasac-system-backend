package com.paulfernandosr.possystembackend.product.domain.port.input;

public interface DeleteProductImageUseCase {
    void deleteProductImage(Long productId, Long imageId);
}
