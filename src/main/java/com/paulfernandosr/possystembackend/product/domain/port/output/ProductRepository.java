package com.paulfernandosr.possystembackend.product.domain.port.output;

import com.paulfernandosr.possystembackend.common.domain.Page;
import com.paulfernandosr.possystembackend.common.domain.Pageable;
import com.paulfernandosr.possystembackend.product.domain.Product;

import java.util.Collection;
import java.util.Optional;

public interface ProductRepository {

    void create(Product product);

    Page<Product> findPage(String query, Pageable pageable);

    Optional<Product> findById(Long productId);

    Collection<Product> findByIdIn(Collection<Long> productIds);

    void updateById(Long productId, Product product);
}
