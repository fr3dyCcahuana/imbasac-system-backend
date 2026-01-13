package com.paulfernandosr.possystembackend.sale.infrastructure.adapter.output.sunat;

import lombok.Getter;

@Getter
public enum CurrencyType {
    SOL("1"),
    DOLLAR("2");

    private final String code;

    CurrencyType(String code) {
        this.code = code;
    }
}
