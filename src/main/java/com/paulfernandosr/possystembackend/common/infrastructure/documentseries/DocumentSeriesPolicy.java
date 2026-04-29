package com.paulfernandosr.possystembackend.common.infrastructure.documentseries;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

@Component
@RequiredArgsConstructor
public class DocumentSeriesPolicy {

    private final DocumentSeriesProperties properties;

    public String requireAllowed(String docType,
                                 String series,
                                 Function<String, ? extends RuntimeException> exceptionFactory) {
        String normalizedDocType = normalizeRequired(docType, "docType", exceptionFactory);
        String normalizedSeries = normalizeRequired(series, "series", exceptionFactory);
        DocumentSeriesProperties.DocumentConfig config = getRequiredConfig(normalizedDocType, exceptionFactory);
        Set<String> allowed = normalizedAllowedSeries(normalizedDocType, config, exceptionFactory);

        if (!allowed.contains(normalizedSeries)) {
            throw exceptionFactory.apply("Serie no permitida para docType=" + normalizedDocType
                    + ". series=" + normalizedSeries
                    + ". Series permitidas: " + String.join(", ", allowed));
        }

        return normalizedSeries;
    }

    public String resolveOrDefault(String docType,
                                   String requestedSeries,
                                   Function<String, ? extends RuntimeException> exceptionFactory) {
        String normalizedDocType = normalizeRequired(docType, "docType", exceptionFactory);
        DocumentSeriesProperties.DocumentConfig config = getRequiredConfig(normalizedDocType, exceptionFactory);
        Set<String> allowed = normalizedAllowedSeries(normalizedDocType, config, exceptionFactory);

        String normalizedRequested = normalizeToNull(requestedSeries);
        if (normalizedRequested == null) {
            String defaultSeries = normalizeToNull(config.getDefaultSeries());
            if (defaultSeries != null) {
                return requireAllowed(normalizedDocType, defaultSeries, exceptionFactory);
            }
            if (allowed.size() == 1) {
                return allowed.iterator().next();
            }
            throw exceptionFactory.apply("Debe enviar series para docType=" + normalizedDocType
                    + " porque no existe default-series configurado y hay más de una serie permitida: "
                    + String.join(", ", allowed));
        }

        return requireAllowed(normalizedDocType, normalizedRequested, exceptionFactory);
    }

    public boolean hasConfigFor(String docType) {
        String normalizedDocType = normalizeToNull(docType);
        return normalizedDocType != null && findConfig(normalizedDocType) != null;
    }

    public String normalizeSeries(String series,
                                  Function<String, ? extends RuntimeException> exceptionFactory) {
        return normalizeRequired(series, "series", exceptionFactory);
    }

    private DocumentSeriesProperties.DocumentConfig getRequiredConfig(
            String normalizedDocType,
            Function<String, ? extends RuntimeException> exceptionFactory) {
        DocumentSeriesProperties.DocumentConfig config = findConfig(normalizedDocType);
        if (config == null) {
            throw exceptionFactory.apply("No existe configuración app.document-series.documents."
                    + normalizedDocType + " para validar series permitidas.");
        }
        return config;
    }

    private DocumentSeriesProperties.DocumentConfig findConfig(String normalizedDocType) {
        if (properties.getDocuments() == null || properties.getDocuments().isEmpty()) {
            return null;
        }
        for (Map.Entry<String, DocumentSeriesProperties.DocumentConfig> entry : properties.getDocuments().entrySet()) {
            if (normalizeToNull(entry.getKey()) != null
                    && normalizeToNull(entry.getKey()).equals(normalizedDocType)) {
                return entry.getValue();
            }
        }
        return null;
    }

    private Set<String> normalizedAllowedSeries(String normalizedDocType,
                                                DocumentSeriesProperties.DocumentConfig config,
                                                Function<String, ? extends RuntimeException> exceptionFactory) {
        LinkedHashSet<String> allowed = new LinkedHashSet<>();
        if (config.getAllowedSeries() != null) {
            for (String value : config.getAllowedSeries()) {
                String normalized = normalizeToNull(value);
                if (normalized != null) {
                    allowed.add(normalized);
                }
            }
        }
        String defaultSeries = normalizeToNull(config.getDefaultSeries());
        if (defaultSeries != null) {
            allowed.add(defaultSeries);
        }
        if (allowed.isEmpty()) {
            throw exceptionFactory.apply("No existen series permitidas configuradas para docType="
                    + normalizedDocType + ". Configure allowed-series o default-series.");
        }
        return allowed;
    }

    private String normalizeRequired(String value,
                                     String fieldName,
                                     Function<String, ? extends RuntimeException> exceptionFactory) {
        String normalized = normalizeToNull(value);
        if (normalized == null) {
            throw exceptionFactory.apply(fieldName + " es obligatorio.");
        }
        return normalized;
    }

    private String normalizeToNull(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        if (trimmed.isEmpty()) return null;
        return trimmed.toUpperCase(Locale.ROOT);
    }
}
