package com.paulfernandosr.possystembackend.countersale.domain.port.output;

import com.paulfernandosr.possystembackend.countersale.domain.model.ProductSnapshot;

public interface ProductSnapshotRepository {
    ProductSnapshot findSnapshotById(Long productId);
}
