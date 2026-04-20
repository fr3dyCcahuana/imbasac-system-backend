package com.paulfernandosr.possystembackend.product.domain;

import lombok.*;

import java.math.BigDecimal;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductPriceTypeInfo {

    private String code;
    private String name;
    private String description;
    private BigDecimal currentValue;
}
