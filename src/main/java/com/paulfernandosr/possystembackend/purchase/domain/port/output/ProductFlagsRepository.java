package com.paulfernandosr.possystembackend.purchase.domain.port.output;

import com.paulfernandosr.possystembackend.purchase.domain.model.ProductFlags;

import java.util.Optional;

public interface ProductFlagsRepository {
    Optional<ProductFlags> findById(Long productId);
}
