package com.paulfernandosr.possystembackend.salev2.domain.model;

public enum DocType {
    BOLETA,
    FACTURA,
    SIMPLE;

    public boolean isSunat() {
        return this == BOLETA || this == FACTURA;
    }
}
