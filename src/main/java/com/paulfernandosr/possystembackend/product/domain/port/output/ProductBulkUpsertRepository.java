package com.paulfernandosr.possystembackend.product.domain.port.output;

import com.paulfernandosr.possystembackend.product.domain.Product;

import java.util.Set;

public interface ProductBulkUpsertRepository {
    Set<String> findExistingSkus(Set<String> skus);
    void upsertBySku(Product product);
}
