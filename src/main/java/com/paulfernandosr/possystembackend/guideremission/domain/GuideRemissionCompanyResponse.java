package com.paulfernandosr.possystembackend.guideremission.domain;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class GuideRemissionCompanyResponse {
    private String ruc;
    private String razonSocial;
    private String nombreComercial;
    private String domicilioFiscal;
    private String ubigeo;
    private String urbanizacion;
    private String distrito;
    private String provincia;
    private String departamento;
    private Integer modo;
}
