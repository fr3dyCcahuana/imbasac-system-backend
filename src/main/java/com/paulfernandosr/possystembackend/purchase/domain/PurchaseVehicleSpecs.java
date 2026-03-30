package com.paulfernandosr.possystembackend.purchase.domain;

import lombok.*;

import java.math.BigDecimal;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PurchaseVehicleSpecs {
    private String vehicleType;
    private String bodywork;
    private BigDecimal engineCapacity;
    private String fuel;
    private Integer cylinders;

    private BigDecimal netWeight;
    private BigDecimal payload;
    private BigDecimal grossWeight;

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
