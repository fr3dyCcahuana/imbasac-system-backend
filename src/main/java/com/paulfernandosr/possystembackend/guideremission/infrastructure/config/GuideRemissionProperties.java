package com.paulfernandosr.possystembackend.guideremission.infrastructure.config;

import com.paulfernandosr.possystembackend.guideremission.domain.GuideRemissionCompany;
import com.paulfernandosr.possystembackend.guideremission.infrastructure.GuideRemissionRedisConstants;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Getter
@Setter
@Validated
@ConfigurationProperties(prefix = "integration.guide-remission")
public class GuideRemissionProperties {
    /**
     * Ejemplo: http://localhost/API_SUNAT_GUIAS
     */
    @NotBlank
    private String baseUrl;

    /**
     * Timeout total de lectura en segundos.
     */
    private int readTimeoutSeconds = 60;

    /**
     * Timeout de conexión en segundos.
     */
    private int connectTimeoutSeconds = 10;

    /**
     * Milisegundos a esperar entre el submit y la consulta del ticket.
     */
    private long ticketQueryDelayMillis = 1000;

    /**
     * Restar algunos segundos al expires_in antes de grabarlo en Redis.
     */
    private long tokenTtlSkewSeconds = 60;

    /**
     * TTL de respaldo cuando el servicio no informa expires_in.
     */
    private long cachedTokenTtlSecondsFallback = 3300;

    /**
     * Prefijo Redis para el token de guía.
     */
    private String redisKeyPrefix = GuideRemissionRedisConstants.DEFAULT_TOKEN_PREFIX;

    @Valid
    @NotNull
    private Auth auth = new Auth();

    @Valid
    @NotNull
    private Company company = new Company();

    public GuideRemissionCompany toCompanyPayload() {
        return GuideRemissionCompany.builder()
                .ruc(company.getRuc())
                .razonSocial(company.getRazonSocial())
                .nombreComercial(company.getNombreComercial())
                .domicilioFiscal(company.getDomicilioFiscal())
                .ubigeo(company.getUbigeo())
                .urbanizacion(company.getUrbanizacion())
                .distrito(company.getDistrito())
                .provincia(company.getProvincia())
                .departamento(company.getDepartamento())
                .modo(company.getModo())
                .usuSecundarioProduccionUser(auth.getUsuSecundarioProduccionUser())
                .usuSecundarioProduccionPassword(auth.getUsuSecundarioProduccionPassword())
                .guiasClientId(auth.getGuiasClientId())
                .guiasClientSecret(auth.getGuiasClientSecret())
                .build();
    }

    @Getter
    @Setter
    public static class Auth {
        @NotBlank
        private String guiasClientId;
        @NotBlank
        private String guiasClientSecret;
        @NotBlank
        private String usuSecundarioProduccionUser;
        @NotBlank
        private String usuSecundarioProduccionPassword;
    }

    @Getter
    @Setter
    public static class Company {
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
        @NotNull
        private Integer modo = 1;
    }
}
