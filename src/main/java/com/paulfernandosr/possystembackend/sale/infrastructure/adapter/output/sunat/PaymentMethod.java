package com.paulfernandosr.possystembackend.sale.infrastructure.adapter.output.sunat;

import lombok.Getter;

@Getter
public enum PaymentMethod {
    CASH("1"),
    CREDIT("2");

    private final String code;

    PaymentMethod(String code) {
        this.code = code;
    }
}
