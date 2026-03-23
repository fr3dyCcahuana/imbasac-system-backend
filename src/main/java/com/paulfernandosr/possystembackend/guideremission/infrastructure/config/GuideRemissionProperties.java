package com.paulfernandosr.possystembackend.guideremission.infrastructure.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "integration.guide-remission")
public class GuideRemissionProperties {
    /**
     * Ejemplo: http://localhost/API_SUNAT_GUIAS
     */
    private String baseUrl;

    /**
     * Timeout total de lectura en segundos.
     */
    private int readTimeoutSeconds = 60;

    /**
     * Timeout de conexión en segundos.
     */
    private int connectTimeoutSeconds = 10;
}
