package com.paulfernandosr.possystembackend.purchase.domain.exception;

import lombok.Getter;

import java.util.Collections;
import java.util.List;

@Getter
public class PurchaseApiException extends RuntimeException {

    private final int status;
    private final String code;
    private final List<PurchaseFieldError> errors;

    public PurchaseApiException(int status, String code, String message) {
        this(status, code, message, List.of());
    }

    public PurchaseApiException(int status, String code, String message, List<PurchaseFieldError> errors) {
        super(message);
        this.status = status;
        this.code = code;
        this.errors = errors == null ? Collections.emptyList() : List.copyOf(errors);
    }
}
