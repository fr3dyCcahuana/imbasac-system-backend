package com.paulfernandosr.possystembackend.proformav2.domain.port.output;

import com.paulfernandosr.possystembackend.proformav2.infrastructure.adapter.output.model.ProductSnapshot;

public interface ProductSnapshotRepository {
    ProductSnapshot getById(Long productId);
}
