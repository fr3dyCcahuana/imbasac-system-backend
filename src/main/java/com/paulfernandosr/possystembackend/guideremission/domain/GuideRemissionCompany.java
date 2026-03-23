package com.paulfernandosr.possystembackend.guideremission.domain;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import jakarta.validation.constraints.NotBlank;
import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class GuideRemissionCompany {
    @NotBlank
    private String ruc;

    @NotBlank
    private String razonSocial;

    private String nombreComercial;

    @NotBlank
    private String domicilioFiscal;

    @NotBlank
    private String ubigeo;

    private String urbanizacion;

    @NotBlank
    private String distrito;

    @NotBlank
    private String provincia;

    @NotBlank
    private String departamento;

    private Integer modo;
    private String usuSecundarioProduccionUser;
    private String usuSecundarioProduccionPassword;
    private String guiasClientId;
    private String guiasClientSecret;
}
