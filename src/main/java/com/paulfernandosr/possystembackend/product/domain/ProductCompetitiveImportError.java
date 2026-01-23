package com.paulfernandosr.possystembackend.product.domain;

import lombok.*;

import java.util.Map;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductCompetitiveImportError {

    private int row;
    private String sku;

    private String field;      // "Precio A", "Categoría", etc.
    private String code;       // "REQUIRED", "NOT_ALLOWED", "BELOW_MIN_PRICE", etc.
    private Object value;

    private Map<String, Object> context;

    private String message;
}
