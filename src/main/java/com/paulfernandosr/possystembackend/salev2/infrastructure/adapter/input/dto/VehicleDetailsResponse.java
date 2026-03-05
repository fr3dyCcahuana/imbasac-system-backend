package com.paulfernandosr.possystembackend.salev2.infrastructure.adapter.input.dto;

import lombok.*;

import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class VehicleDetailsResponse {
    private String marca;
    private String color;
    private String modelo;

    private String numMotor;
    private String numChasis;
    private String numVin;

    private String dua;
    private Integer itemDua;

    private Integer anioFabricacion;

    private String capacidadMotor;   // lo formateamos tipo "150CC"
    private String combustible;

    private Integer numCilindros;

    private BigDecimal pesoNeto;
    private BigDecimal pesoBruto;
}