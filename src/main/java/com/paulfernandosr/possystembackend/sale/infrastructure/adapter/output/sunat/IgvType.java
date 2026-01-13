package com.paulfernandosr.possystembackend.sale.infrastructure.adapter.output.sunat;

import lombok.Getter;

@Getter
public enum IgvType {
    TAXABLE_ONEROUS("10");

    private final String code;

    IgvType(String code) {
        this.code = code;
    }
}
