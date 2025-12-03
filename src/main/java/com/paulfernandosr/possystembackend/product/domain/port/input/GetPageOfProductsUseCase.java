package com.paulfernandosr.possystembackend.product.domain.port.input;

import com.paulfernandosr.possystembackend.common.domain.Page;
import com.paulfernandosr.possystembackend.common.domain.Pageable;
import com.paulfernandosr.possystembackend.product.domain.Product;

public interface GetPageOfProductsUseCase {
    Page<Product> getPageOfProducts(String query, Pageable pageable);
}
