package com.paulfernandosr.possystembackend.sale.infrastructure.adapter.output.sunat;

import com.paulfernandosr.possystembackend.sale.domain.SaleType;
import lombok.Getter;

@Getter
public enum DocumentType {
    INVOICE("01"),
    RECEIPT("03"),
    CREDIT_NOTE("07");

    private final String code;

    DocumentType(String code) {
        this.code = code;
    }

    public static DocumentType fromSaleType(SaleType saleType) {
        return switch (saleType) {
            case ELECTRONIC_RECEIPT -> RECEIPT;
            case ELECTRONIC_INVOICE -> INVOICE;
            default -> throw new RuntimeException("Invalid sale type");
        };
    }
}
