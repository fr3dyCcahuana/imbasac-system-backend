package com.paulfernandosr.possystembackend.product.application;

import com.paulfernandosr.possystembackend.product.domain.exception.ProductImageNotFoundException;
import com.paulfernandosr.possystembackend.product.domain.port.input.DeleteProductImageUseCase;
import com.paulfernandosr.possystembackend.product.domain.port.output.ProductImageRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class DeleteProductImageService implements DeleteProductImageUseCase {

    private final ProductImageRepository productImageRepository;

    @Override
    public void deleteProductImage(Long productId, Long imageId) {
        var existing = productImageRepository.findByIdAndProductId(imageId, productId)
                .orElseThrow(() -> new ProductImageNotFoundException(
                        "Image not found with id: " + imageId + " for product: " + productId
                ));

        productImageRepository.deleteByIdAndProductId(existing.getId(), productId);
    }
}
