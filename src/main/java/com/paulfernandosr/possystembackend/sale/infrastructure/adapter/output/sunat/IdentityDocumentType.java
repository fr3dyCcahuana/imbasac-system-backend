package com.paulfernandosr.possystembackend.sale.infrastructure.adapter.output.sunat;

import lombok.Getter;

@Getter
public enum IdentityDocumentType {
    DNI("1"),
    CE("4"),
    RUC("6"),
    PASSPORT("7");

    private final String code;

    IdentityDocumentType(String code) {
        this.code = code;
    }
}
