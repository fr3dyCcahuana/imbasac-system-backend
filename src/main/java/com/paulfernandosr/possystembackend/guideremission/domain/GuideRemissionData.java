package com.paulfernandosr.possystembackend.guideremission.domain;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class GuideRemissionData {
        private String serie;

    private String numero;

    @NotBlank
    private String fechaEmision;

    @NotBlank
    private String horaEmision;

    @NotBlank
    private String fechaTraslado;

    @NotBlank
    private String guiaMotivoTraslado;

    @NotBlank
    private String guiaModalidadTraslado;

    // Legado / privado
    private String entidadIdTransporte;
    private String numeroMtcTransporte;

    // Público (payload que compartiste)
    private String numeroDocumentoTransporte;
    private String entidadTransporte;

    private String conductorDni;
    private String conductorNombres;
    private String conductorApellidos;
    private String conductorLicencia;
    private String vehiculoPlaca;

    @NotNull
    private Integer destinatarioTipo;

    @NotBlank
    private String destinatarioNumeroDocumento;

    @NotBlank
    private String destinatarioNombresRazon;

    @NotBlank
    private String partidaUbigeo;

    @NotBlank
    private String partidaDireccion;

    private String partidaCodigoEstablecimiento;

    @NotBlank
    private String llegadaUbigeo;

    @NotBlank
    private String llegadaDireccion;

    private String llegadaCodigoEstablecimiento;

    @NotBlank
    private String pesoTotal;

    private String numeroBultos;
    private String notas;
}
