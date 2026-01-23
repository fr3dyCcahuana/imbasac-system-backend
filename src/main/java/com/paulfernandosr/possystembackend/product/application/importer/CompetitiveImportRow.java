package com.paulfernandosr.possystembackend.product.application.importer;

import lombok.*;

import java.math.BigDecimal;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CompetitiveImportRow {
    private int rowNumber; // número de fila real en Excel (1-based)
    private String sku;
    private String name;

    private String category;
    private String presentation;
    private BigDecimal factor;

    private String originType;
    private String originCountry;

    private String compatibility;
    private String warehouseLocation;

    private BigDecimal competPublic;      // CROLANDO PUBLICO
    private BigDecimal priceA;
    private BigDecimal priceB;

    private BigDecimal competWholesale;   // CROSLAND MAYORISTA
    private BigDecimal priceC;
    private BigDecimal priceD;

    private BigDecimal costReference;
}
