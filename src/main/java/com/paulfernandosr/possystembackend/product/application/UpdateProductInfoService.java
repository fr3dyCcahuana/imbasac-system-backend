package com.paulfernandosr.possystembackend.product.application;

import com.paulfernandosr.possystembackend.product.domain.Product;
import com.paulfernandosr.possystembackend.product.domain.port.input.UpdateProductInfoUseCase;
import com.paulfernandosr.possystembackend.product.domain.port.output.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class UpdateProductInfoService implements UpdateProductInfoUseCase {

    private final ProductRepository productRepository;

    @Override
    public void updateProductInfoById(Long productId, Product product) {
        productRepository.updateById(productId, product);
    }
}
