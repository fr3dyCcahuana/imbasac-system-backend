package com.paulfernandosr.possystembackend.sale.infrastructure.adapter.output.sunat;

import lombok.Getter;

@Getter
public enum UnitOfMeasureType {
    PRODUCT_UNIT("NIU"),
    SERVICE_UNIT("ZZ");

    private final String code;

    UnitOfMeasureType(String code) {
        this.code = code;
    }
}
