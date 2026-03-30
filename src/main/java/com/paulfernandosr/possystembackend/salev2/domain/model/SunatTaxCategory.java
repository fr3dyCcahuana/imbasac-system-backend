package com.paulfernandosr.possystembackend.salev2.domain.model;

import lombok.Getter;

@Getter
public enum SunatTaxCategory {
    GRAVADA("10"),
    EXONERADA("20"),
    INAFECTA("30");

    private final String itemIgvTypeCode;

    SunatTaxCategory(String itemIgvTypeCode) {
        this.itemIgvTypeCode = itemIgvTypeCode;
    }
}
