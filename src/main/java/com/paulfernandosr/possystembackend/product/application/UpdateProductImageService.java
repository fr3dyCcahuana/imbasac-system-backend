package com.paulfernandosr.possystembackend.product.application;

import com.paulfernandosr.possystembackend.product.domain.ProductImage;
import com.paulfernandosr.possystembackend.product.domain.exception.ProductImageNotFoundException;
import com.paulfernandosr.possystembackend.product.domain.port.input.UpdateProductImageUseCase;
import com.paulfernandosr.possystembackend.product.domain.port.output.ProductImageRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class UpdateProductImageService implements UpdateProductImageUseCase {

    private final ProductImageRepository productImageRepository;

    @Override
    public void updateProductImage(Long productId, Long imageId, ProductImage image) {
        ProductImage existing = productImageRepository.findByIdAndProductId(imageId, productId)
                .orElseThrow(() -> new ProductImageNotFoundException(
                        "Image not found with id: " + imageId + " for product: " + productId
                ));

        // Merge: si no envían valor, se conserva el existente
        String imageUrl = image.getImageUrl() != null ? image.getImageUrl() : existing.getImageUrl();
        Short position = image.getPosition() != null ? image.getPosition() : existing.getPosition();
        Boolean isMain = image.getIsMain() != null ? image.getIsMain() : existing.getIsMain();

        ProductImage merged = ProductImage.builder()
                .id(existing.getId())
                .productId(existing.getProductId())
                .imageUrl(imageUrl)
                .position(position)
                .isMain(isMain)
                .createdAt(existing.getCreatedAt())
                .build();

        productImageRepository.update(merged);
    }
}
