package com.paulfernandosr.possystembackend.contracts.infrastructure.adapter.input.dto;

import lombok.*;

import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class VehicleSpecsDto {

    private Long productId;

    private String vehicleType;   // MOTOR | MOTOCICLETA
    private String bodywork;

    private BigDecimal engineCapacity;
    private String fuel;
    private Integer cylinders;

    private BigDecimal netWeight;
    private BigDecimal payload;
    private BigDecimal grossWeight;

    // Solo MOTOCICLETA
    private String vehicleClass;
    private BigDecimal enginePower;
    private String rollingForm;

    private Integer seats;
    private Integer passengers;
    private Integer axles;
    private Integer wheels;

    private BigDecimal length;
    private BigDecimal width;
    private BigDecimal height;
}
