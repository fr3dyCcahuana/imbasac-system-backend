package com.paulfernandosr.possystembackend.product.application;

import com.paulfernandosr.possystembackend.product.domain.ProductImage;
import com.paulfernandosr.possystembackend.product.domain.port.input.CreateProductImageUseCase;
import com.paulfernandosr.possystembackend.product.domain.port.output.ProductImageRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class CreateProductImageService implements CreateProductImageUseCase {

    private final ProductImageRepository productImageRepository;

    @Override
    public void createProductImage(Long productId, ProductImage image) {
        image.setId(null);
        image.setProductId(productId);
        productImageRepository.create(image);
    }
}
