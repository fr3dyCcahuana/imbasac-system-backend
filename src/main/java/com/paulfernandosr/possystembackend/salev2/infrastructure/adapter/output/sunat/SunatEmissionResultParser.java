package com.paulfernandosr.possystembackend.salev2.infrastructure.adapter.output.sunat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
@RequiredArgsConstructor
public class SunatEmissionResultParser {

    private static final String SUCCESS_RESPONSE = "0";
    private static final Pattern SUNAT_INFO_CODE_PATTERN = Pattern.compile("(?i)\\b(?:INFO|ERROR)\\s*:?\\s*(\\d{3,5})\\b");
    private static final Pattern SUNAT_PREFIX_CODE_PATTERN = Pattern.compile("^\\s*(\\d{3,5})\\s*-");

    private final ObjectMapper objectMapper;

    public SunatEmissionResult parse(String rawResponse, LocalDateTime emittedAt) {
        try {
            if (rawResponse == null || rawResponse.isBlank()) {
                return communication("Respuesta vacía de SUNAT/PHP.", null, null, null, emittedAt);
            }

            JsonNode root = objectMapper.readTree(rawResponse);
            JsonNode data = root != null && root.has("data") ? root.path("data") : root;

            String providerError = firstNonBlank(textValue(data, "error"), textValue(root, "error"));
            String detail = firstNonBlank(
                    textValue(data, "detalle"),
                    textValue(root, "detalle"),
                    textValue(data, "raw"),
                    textValue(root, "raw")
            );

            String xmlPath = firstNonBlank(textValue(data, "ruta_xml"), textValue(root, "ruta_xml"));
            String cdrPath = firstNonBlank(textValue(data, "ruta_cdr"), textValue(root, "ruta_cdr"));
            String pdfPath = firstNonBlank(textValue(data, "ruta_pdf"), textValue(root, "ruta_pdf"));
            String hashCode = extractHashCode(data != null ? data.path("codigo_hash") : null);

            if (providerError != null) {
                String description = "Error de comunicación con SUNAT: " + providerError
                        + (detail != null ? " - " + detail : "");
                String embeddedCode = extractSunatErrorCode(description);
                if (embeddedCode != null) {
                    return rejected(embeddedCode, description, hashCode, xmlPath, cdrPath, pdfPath, emittedAt);
                }
                return communication(description, xmlPath, cdrPath, pdfPath, emittedAt);
            }

            String code = firstNonBlank(
                    textValue(data, "respuesta_sunat_codigo"),
                    textValue(root, "respuesta_sunat_codigo")
            );
            String description = firstNonBlank(
                    textValue(data, "respuesta_sunat_descripcion"),
                    textValue(root, "respuesta_sunat_descripcion"),
                    "Respuesta vacía de SUNAT"
            );
            if (code == null || code.isBlank()) {
                return communication(
                        "SUNAT/PHP no devolvió código de respuesta. " + description,
                        xmlPath,
                        cdrPath,
                        pdfPath,
                        emittedAt
                );
            }

            if (SUCCESS_RESPONSE.equals(code)) {
                return SunatEmissionResult.builder()
                        .status("ACEPTADO")
                        .code(code)
                        .description(description)
                        .hashCode(hashCode)
                        .xmlPath(xmlPath)
                        .cdrPath(cdrPath)
                        .pdfPath(pdfPath)
                        .emittedAt(emittedAt)
                        .accepted(true)
                        .rejected(false)
                        .communicationError(false)
                        .retryable(false)
                        .build();
            }

            return rejected(code, description, hashCode, xmlPath, cdrPath, pdfPath, emittedAt);

        } catch (Exception ex) {
            return communication(
                    "No se pudo interpretar la respuesta de SUNAT/PHP: " + safeMessage(ex),
                    null,
                    null,
                    null,
                    emittedAt
            );
        }
    }

    public SunatEmissionResult fromException(Exception ex, LocalDateTime emittedAt) {
        return communication(
                "Error de comunicación con SUNAT/PHP: " + safeMessage(ex),
                null,
                null,
                null,
                emittedAt
        );
    }

    private SunatEmissionResult rejected(String code,
                                         String description,
                                         String hashCode,
                                         String xmlPath,
                                         String cdrPath,
                                         String pdfPath,
                                         LocalDateTime emittedAt) {
        return SunatEmissionResult.builder()
                .status("RECHAZADO")
                .code(code)
                .description(description)
                .hashCode(hashCode)
                .xmlPath(xmlPath)
                .cdrPath(cdrPath)
                .pdfPath(pdfPath)
                .emittedAt(emittedAt)
                .accepted(false)
                .rejected(true)
                .communicationError(false)
                .retryable(false)
                .build();
    }

    private SunatEmissionResult communication(String description,
                                              String xmlPath,
                                              String cdrPath,
                                              String pdfPath,
                                              LocalDateTime emittedAt) {
        return SunatEmissionResult.builder()
                .status("ERROR_COMUNICACION")
                .code(null)
                .description(description)
                .hashCode(null)
                .xmlPath(xmlPath)
                .cdrPath(cdrPath)
                .pdfPath(pdfPath)
                .emittedAt(emittedAt)
                .accepted(false)
                .rejected(false)
                .communicationError(true)
                .retryable(true)
                .build();
    }

    private String textValue(JsonNode node, String fieldName) {
        if (node == null || node.isMissingNode() || node.isNull()) return null;
        JsonNode child = node.path(fieldName);
        if (child.isMissingNode() || child.isNull()) return null;
        String value = child.asText();
        return value == null || value.isBlank() ? null : value.trim();
    }

    private String extractHashCode(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) return null;

        if (node.isArray()) {
            for (JsonNode item : node) {
                if (item != null && !item.isNull()) {
                    String value = item.asText();
                    if (value != null && !value.isBlank()) return value.trim();
                }
            }
            return null;
        }

        if (node.isObject()) {
            String code = textValue(node, "codigo");
            if (code != null) return code;

            String hash = textValue(node, "hash");
            if (hash != null) return hash;
        }

        String value = node.asText();
        return value == null || value.isBlank() ? null : value.trim();
    }

    private String firstNonBlank(String... values) {
        if (values == null) return null;
        for (String value : values) {
            if (value != null && !value.isBlank()) return value.trim();
        }
        return null;
    }

    private String extractSunatErrorCode(String text) {
        if (text == null || text.isBlank()) return null;

        Matcher infoMatcher = SUNAT_INFO_CODE_PATTERN.matcher(text);
        if (infoMatcher.find()) {
            return infoMatcher.group(1);
        }

        Matcher prefixMatcher = SUNAT_PREFIX_CODE_PATTERN.matcher(text);
        if (prefixMatcher.find()) {
            return prefixMatcher.group(1);
        }

        return null;
    }

    private String safeMessage(Exception ex) {
        if (ex == null) return "Error desconocido";
        String message = ex.getMessage();
        return message == null || message.isBlank() ? ex.getClass().getSimpleName() : message;
    }
}
