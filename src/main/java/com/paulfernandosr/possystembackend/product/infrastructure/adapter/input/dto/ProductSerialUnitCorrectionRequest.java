package com.paulfernandosr.possystembackend.product.infrastructure.adapter.input.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ProductSerialUnitCorrectionRequest {

    private String vin;
    private String chassisNumber;
    private String engineNumber;

    private String color;
    private Integer yearMake;

    private String duaNumber;
    private Integer duaItem;

    private String reason;
}
