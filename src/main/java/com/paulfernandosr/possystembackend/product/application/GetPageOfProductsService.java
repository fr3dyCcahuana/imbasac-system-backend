package com.paulfernandosr.possystembackend.product.application;

import com.paulfernandosr.possystembackend.common.domain.Page;
import com.paulfernandosr.possystembackend.common.domain.Pageable;
import com.paulfernandosr.possystembackend.product.domain.Product;
import com.paulfernandosr.possystembackend.product.domain.port.input.GetPageOfProductsUseCase;
import com.paulfernandosr.possystembackend.product.domain.port.output.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class GetPageOfProductsService implements GetPageOfProductsUseCase {

    private final ProductRepository productRepository;

    @Override
    public Page<Product> getPageOfProducts(String query, Pageable pageable) {
        return productRepository.findPage(query, pageable);
    }
}
