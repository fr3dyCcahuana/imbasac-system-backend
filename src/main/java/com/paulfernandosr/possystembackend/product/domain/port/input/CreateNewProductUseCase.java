package com.paulfernandosr.possystembackend.product.domain.port.input;

import com.paulfernandosr.possystembackend.product.domain.Product;

public interface CreateNewProductUseCase {
    Product createNewProduct(Product product);  // ✅ antes: void
}