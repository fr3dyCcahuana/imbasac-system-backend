package com.paulfernandosr.possystembackend.salev2.domain.port.output;

import com.paulfernandosr.possystembackend.salev2.domain.model.ProductSnapshot;

import java.util.Optional;

public interface ProductSnapshotRepository {
    Optional<ProductSnapshot> findById(Long productId);
}
