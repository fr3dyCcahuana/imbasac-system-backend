package com.paulfernandosr.possystembackend.common.infrastructure.documentseries;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "app.document-series")
public class DocumentSeriesProperties {

    /**
     * Configuración central por tipo de documento.
     * La tabla document_series sigue siendo la fuente para habilitación y correlativo.
     */
    private Map<String, DocumentConfig> documents = new LinkedHashMap<>();

    @Getter
    @Setter
    public static class DocumentConfig {
        /**
         * Serie usada cuando el flujo permite omitir series en el request.
         */
        private String defaultSeries;

        /**
         * Series permitidas por negocio para el tipo de documento.
         */
        private List<String> allowedSeries = new ArrayList<>();
    }
}
