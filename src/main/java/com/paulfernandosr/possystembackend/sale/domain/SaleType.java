package com.paulfernandosr.possystembackend.sale.domain;

import lombok.Getter;

@Getter
public enum SaleType {
    SIMPLE_RECEIPT("B003"),
    ELECTRONIC_RECEIPT("B003"),
    ELECTRONIC_INVOICE("F003");

    private final String serial;

    SaleType(String serial) {
        this.serial = serial;
    }

    public boolean isReceipt() {
        return SIMPLE_RECEIPT.equals(this)
                || ELECTRONIC_RECEIPT.equals(this);
    }
}
