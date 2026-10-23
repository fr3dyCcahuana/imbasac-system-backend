package com.paulfernandosr.possystembackend.salev2.domain.exception;

import java.util.UUID;

/** A known business rejection; the outer sale transaction is rolled back before returning it. */
public class SaleCreateRejectedException extends RuntimeException {
    private final UUID idempotencyKey;

    public SaleCreateRejectedException(UUID idempotencyKey, RuntimeException cause) {
        super(cause.getMessage(), cause);
        this.idempotencyKey = idempotencyKey;
    }

    public UUID getIdempotencyKey() {
        return idempotencyKey;
    }
}
