package com.paulfernandosr.possystembackend.salev2.domain.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

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
    private String presentation;
    private BigDecimal factor;

    private BigDecimal priceA;
    private BigDecimal priceB;
    private BigDecimal priceC;
    private BigDecimal priceD;

    private boolean facturableSunat;
    private boolean affectsStock;
    private boolean giftAllowed;
}
