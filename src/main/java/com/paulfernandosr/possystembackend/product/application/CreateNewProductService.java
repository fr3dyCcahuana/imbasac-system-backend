package com.paulfernandosr.possystembackend.product.application;

import com.paulfernandosr.possystembackend.product.domain.Product;
import com.paulfernandosr.possystembackend.product.domain.port.input.CreateNewProductUseCase;
import com.paulfernandosr.possystembackend.product.domain.port.output.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class CreateNewProductService implements CreateNewProductUseCase {

    private final ProductRepository productRepository;

    @Override
    public void createNewProduct(Product product) {
        productRepository.create(product);
    }
}
