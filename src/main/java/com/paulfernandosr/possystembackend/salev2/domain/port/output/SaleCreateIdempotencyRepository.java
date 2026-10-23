package com.paulfernandosr.possystembackend.salev2.domain.port.output;

import java.util.UUID;

public interface SaleCreateIdempotencyRepository {
    /** Must run in the transaction that creates the sale; waits for concurrent attempts. */
    Entry acquire(Long userId, UUID key, String requestHash);

    void complete(Long userId, UUID key, Long saleId, String responseJson);

    record Entry(String requestHash, String responseJson) { }
}
