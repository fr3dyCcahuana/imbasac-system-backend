package com.paulfernandosr.possystembackend.salev2.domain.model;

import lombok.*;

import java.math.BigDecimal;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductSnapshot {
    private Long id;
    private String sku;
    private String name;
    private String presentation;
    private BigDecimal factor;

    private Boolean facturableSunat;
    private Boolean affectsStock;
    private Boolean giftAllowed;
    private Boolean manageBySerial;

    private BigDecimal priceA;
    private BigDecimal priceB;
    private BigDecimal priceC;
    private BigDecimal priceD;

    public BigDecimal priceFor(PriceList priceList) {
        return switch (priceList) {
            case A -> priceA;
            case B -> priceB;
            case C -> priceC;
            case D -> priceD;
        };
    }
}
