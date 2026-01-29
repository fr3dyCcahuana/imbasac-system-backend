package com.paulfernandosr.possystembackend.proformav2.infrastructure.adapter.output.model;

import lombok.*;

import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProductSnapshot {
    private Long id;
    private String sku;
    private String name;
    private String category;
    private String presentation;
    private BigDecimal factor;

    private Boolean manageBySerial;
    private Boolean facturableSunat;
    private Boolean affectsStock;

    private BigDecimal priceA;
    private BigDecimal priceB;
    private BigDecimal priceC;
    private BigDecimal priceD;
}
