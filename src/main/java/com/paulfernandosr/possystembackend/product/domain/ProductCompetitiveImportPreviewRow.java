package com.paulfernandosr.possystembackend.product.domain;

import lombok.*;

import java.math.BigDecimal;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductCompetitiveImportPreviewRow {
    private int row;
    private String sku;

    private BigDecimal priceA;
    private BigDecimal priceB;
    private BigDecimal priceC;
    private BigDecimal priceD;

    private BigDecimal costReference;
}
