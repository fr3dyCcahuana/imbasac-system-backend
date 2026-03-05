package com.paulfernandosr.possystembackend.product.infrastructure.adapter.input.dto;

import com.paulfernandosr.possystembackend.product.domain.ProductSerialUnit;
import lombok.*;

import java.math.BigDecimal;
import java.util.List;

@Getter @Setter
@Builder
@NoArgsConstructor @AllArgsConstructor
public class ProductStockValidationDto {

    private Long id;

    private boolean exists;

    private Boolean affectsStock;
    private Boolean manageBySerial;
    private Boolean giftAllowed;
    private Boolean facturableSunat;

    private BigDecimal stockAvailable;
    private boolean inStock;

    private List<ProductSerialUnit> availableSerialUnits;
}