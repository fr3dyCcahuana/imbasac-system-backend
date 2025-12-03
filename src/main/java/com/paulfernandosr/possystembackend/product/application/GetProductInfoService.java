package com.paulfernandosr.possystembackend.product.application;

import com.paulfernandosr.possystembackend.product.domain.Product;
import com.paulfernandosr.possystembackend.product.domain.exception.ProductNotFoundException;
import com.paulfernandosr.possystembackend.product.domain.port.input.GetProductInfoUseCase;
import com.paulfernandosr.possystembackend.product.domain.port.output.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class GetProductInfoService implements GetProductInfoUseCase {

    private final ProductRepository productRepository;

    @Override
    public Product getProductInfoById(Long productId) {
        return productRepository.findById(productId)
                .orElseThrow(() -> new ProductNotFoundException(
                        "Product not found with identification: " + productId
                ));
    }
}
