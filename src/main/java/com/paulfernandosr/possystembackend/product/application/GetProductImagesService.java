package com.paulfernandosr.possystembackend.product.application;

import com.paulfernandosr.possystembackend.product.domain.ProductImage;
import com.paulfernandosr.possystembackend.product.domain.port.input.GetProductImagesUseCase;
import com.paulfernandosr.possystembackend.product.domain.port.output.ProductImageRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Collection;

@Service
@RequiredArgsConstructor
public class GetProductImagesService implements GetProductImagesUseCase {

    private final ProductImageRepository productImageRepository;

    @Override
    public Collection<ProductImage> getImagesByProductId(Long productId) {
        return productImageRepository.findByProductId(productId);
    }
}
