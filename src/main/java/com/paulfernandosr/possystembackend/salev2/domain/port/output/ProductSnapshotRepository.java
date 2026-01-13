package com.paulfernandosr.possystembackend.salev2.domain.port.output;

import com.paulfernandosr.possystembackend.salev2.domain.model.ProductSnapshot;

public interface ProductSnapshotRepository {
    ProductSnapshot findSnapshotById(Long productId);
}
