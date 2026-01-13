package com.paulfernandosr.possystembackend.supplier.domain.exception;

public class SupplierNotFoundException extends RuntimeException {
    public SupplierNotFoundException(String message) {
        super(message);
    }
}
