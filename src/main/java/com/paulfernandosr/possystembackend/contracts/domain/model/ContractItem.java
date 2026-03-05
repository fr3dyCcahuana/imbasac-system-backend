package com.paulfernandosr.possystembackend.contracts.domain.model;

import lombok.*;

import java.math.BigDecimal;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ContractItem {

    private Long id;
    private Long contractId;

    private Long productId;
    private Long serialUnitId;

    private String sku;
    private String description;
    private String brand;
    private String model;

    private String vin;

    private BigDecimal unitPrice;
}
