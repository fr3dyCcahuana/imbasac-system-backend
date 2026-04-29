package com.paulfernandosr.possystembackend.sale.domain;

import lombok.Getter;

@Getter
public enum SaleType {
    SIMPLE_RECEIPT("SIMPLE"),
    ELECTRONIC_RECEIPT("BOLETA"),
    ELECTRONIC_INVOICE("FACTURA");

    private final String docType;

    SaleType(String docType) {
        this.docType = docType;
    }

    public boolean isReceipt() {
        return SIMPLE_RECEIPT.equals(this)
                || ELECTRONIC_RECEIPT.equals(this);
    }

    public boolean isElectronic() {
        return ELECTRONIC_RECEIPT.equals(this)
                || ELECTRONIC_INVOICE.equals(this);
    }
}
