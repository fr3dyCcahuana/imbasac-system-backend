package com.paulfernandosr.possystembackend.guideremission.infrastructure.adapter.output.http;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.paulfernandosr.possystembackend.guideremission.domain.GuideRemissionSubmission;
import com.paulfernandosr.possystembackend.guideremission.domain.GuideRemissionSubmissionResponse;
import com.paulfernandosr.possystembackend.guideremission.domain.GuideRemissionTicketQuery;
import com.paulfernandosr.possystembackend.guideremission.domain.GuideRemissionTicketStatusResponse;
import com.paulfernandosr.possystembackend.guideremission.domain.GuideRemissionTokenResponse;
import com.paulfernandosr.possystembackend.guideremission.domain.exception.GuideRemissionIntegrationException;
import com.paulfernandosr.possystembackend.guideremission.domain.port.output.GuideRemissionProvider;
import com.paulfernandosr.possystembackend.guideremission.infrastructure.config.GuideRemissionProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
public class GuideRemissionHttpProvider implements GuideRemissionProvider {
    private static final int MAX_LOG_BODY_LENGTH = 1500;

    private final RestClient guideRemissionRestClient;
    private final GuideRemissionProperties properties;
    private final ObjectMapper objectMapper;

    @Override
    public GuideRemissionTokenResponse requestToken() {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("guias_client_id", properties.getAuth().getGuiasClientId());
        form.add("guias_client_secret", properties.getAuth().getGuiasClientSecret());
        form.add("ruc", properties.getCompany().getRuc());
        form.add("usu_secundario_produccion_user", properties.getAuth().getUsuSecundarioProduccionUser());
        form.add("usu_secundario_produccion_password", properties.getAuth().getUsuSecundarioProduccionPassword());
        form.add("modo", String.valueOf(properties.resolvedModo()));

        log.info("[guide-remission][token] Solicitud de token. endpoint=/1_solicito_token.php, modo={}, ruc={}, secondaryUser={}",
                properties.resolvedModoLabel(),
                properties.getCompany().getRuc(),
                maskUser(properties.getAuth().getUsuSecundarioProduccionUser()));

        try {
            RawExternalResponse raw = postForString("/1_solicito_token.php", MediaType.APPLICATION_FORM_URLENCODED, form);
            return parseTokenBody(raw);
        } catch (RestClientResponseException ex) {
            throw new GuideRemissionIntegrationException(buildErrorMessage("No se pudo solicitar token de guía de remisión", ex));
        } catch (GuideRemissionIntegrationException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new GuideRemissionIntegrationException("No se pudo solicitar token de guía de remisión", ex);
        }
    }

    @Override
    public GuideRemissionSubmissionResponse submit(GuideRemissionSubmission request) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("empresa", properties.toCompanyPayload());
        payload.put("guia", request.getGuia());
        payload.put("items", request.getItems());
        payload.put("token", request.getToken());

        log.info("[guide-remission][submit] Enviando guía. endpoint=/2_envio_xml_recibe_ticket.php, modo={}, ruc={}, serie={}, numero={}, motivo={}, modalidad={}, items={}",
                properties.resolvedModoLabel(),
                properties.getCompany().getRuc(),
                request.getGuia() != null ? request.getGuia().getSerie() : null,
                request.getGuia() != null ? request.getGuia().getNumero() : null,
                request.getGuia() != null ? request.getGuia().getGuiaMotivoTraslado() : null,
                request.getGuia() != null ? request.getGuia().getGuiaModalidadTraslado() : null,
                request.getItems() != null ? request.getItems().size() : 0);

        try {
            String requestBody = objectMapper.writeValueAsString(payload);
            log.info("[guide-remission][external-request] path=/2_envio_xml_recibe_ticket.php, contentType={}, body={}",
                    MediaType.APPLICATION_JSON,
                    sanitizeForLog(requestBody));

            RawExternalResponse raw = postForString("/2_envio_xml_recibe_ticket.php", MediaType.APPLICATION_JSON, payload);
            return parseSubmissionBody(raw);
        } catch (RestClientResponseException ex) {
            throw new GuideRemissionIntegrationException(buildErrorMessage("No se pudo enviar la guía de remisión", ex));
        } catch (GuideRemissionIntegrationException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new GuideRemissionIntegrationException("No se pudo enviar la guía de remisión", ex);
        }
    }

    @Override
    public GuideRemissionTicketStatusResponse queryTicket(GuideRemissionTicketQuery request) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("ticket", request.getTicket());
        form.add("token_access", request.getTokenAccess());
        form.add("ruc", properties.getCompany().getRuc());
        form.add("serie", request.getSerie());
        form.add("numero", request.getNumero());
        form.add("modo", String.valueOf(properties.resolvedModo()));

        log.info("[guide-remission][ticket] Consultando ticket. endpoint=/3_envio_ticket.php, modo={}, ruc={}, serie={}, numero={}, ticket={}",
                properties.resolvedModoLabel(),
                properties.getCompany().getRuc(),
                request.getSerie(),
                request.getNumero(),
                maskTicket(request.getTicket()));

        try {
            RawExternalResponse raw = postForString("/3_envio_ticket.php", MediaType.APPLICATION_FORM_URLENCODED, form);
            return parseTicketBody(raw);
        } catch (RestClientResponseException ex) {
            throw new GuideRemissionIntegrationException(buildErrorMessage("No se pudo consultar el ticket de la guía", ex));
        } catch (GuideRemissionIntegrationException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new GuideRemissionIntegrationException("No se pudo consultar el ticket de la guía", ex);
        }
    }

    private RawExternalResponse postForString(String path, MediaType contentType, Object body) {
        return guideRemissionRestClient.post()
                .uri(path)
                .contentType(contentType)
                .accept(MediaType.APPLICATION_JSON, MediaType.TEXT_HTML, MediaType.TEXT_PLAIN)
                .body(body)
                .exchange((request, response) -> {
                    String responseBody = response.bodyTo(String.class);
                    HttpStatusCode statusCode = response.getStatusCode();
                    MediaType responseType = response.getHeaders().getContentType();
                    log.info("[guide-remission][external-response] path={}, status={}, contentType={}, body={}",
                            path,
                            statusCode.value(),
                            responseType,
                            sanitizeForLog(responseBody));
                    return new RawExternalResponse(statusCode, responseType, responseBody);
                });
    }

    private GuideRemissionTokenResponse parseTokenBody(RawExternalResponse raw) {
        String responseBody = raw.body();
        if (responseBody == null || responseBody.isBlank()) {
            throw new GuideRemissionIntegrationException("Respuesta vacía en solicitud de token. status=" + raw.statusCode().value());
        }

        String jsonCandidate = extractJsonObject(responseBody);

        try {
            var root = objectMapper.readTree(jsonCandidate);

            return GuideRemissionTokenResponse.builder()
                    .accessToken(text(root, "access_token"))
                    .tokenType(text(root, "token_type"))
                    .expiresIn(longValue(root, "expires_in"))
                    .cod(intValue(root, "cod"))
                    .msg(text(root, "msg"))
                    .exc(text(root, "exc"))
                    .build();
        } catch (Exception ex) {
            throw new GuideRemissionIntegrationException(
                    "No se pudo interpretar la respuesta de solicitud de token. status=" + raw.statusCode().value()
                            + ", contentType=" + raw.contentType()
                            + ", body=" + sanitizeForLog(responseBody), ex);
        }
    }

    private GuideRemissionSubmissionResponse parseSubmissionBody(RawExternalResponse raw) {
        String responseBody = raw.body();
        if (responseBody == null || responseBody.isBlank()) {
            throw new GuideRemissionIntegrationException("Respuesta vacía en envío de guía. status=" + raw.statusCode().value());
        }

        String jsonCandidate = extractJsonObject(responseBody);

        try {
            var root = objectMapper.readTree(jsonCandidate);

            return GuideRemissionSubmissionResponse.builder()
                    .numTicket(text(root, "numTicket"))
                    .fecRecepcion(text(root, "fecRecepcion"))
                    .cod(intValue(root, "cod"))
                    .msg(text(root, "msg"))
                    .exc(text(root, "exc"))
                    .build();
        } catch (Exception ex) {
            throw new GuideRemissionIntegrationException(
                    "No se pudo interpretar la respuesta de envío de guía. status=" + raw.statusCode().value()
                            + ", contentType=" + raw.contentType()
                            + ", body=" + sanitizeForLog(responseBody), ex);
        }
    }

    private GuideRemissionTicketStatusResponse parseTicketBody(RawExternalResponse raw) {
        String responseBody = raw.body();
        if (responseBody == null || responseBody.isBlank()) {
            throw new GuideRemissionIntegrationException("Respuesta vacía en consulta de ticket. status=" + raw.statusCode().value());
        }

        String jsonCandidate = extractJsonObject(responseBody);

        try {
            var root = objectMapper.readTree(jsonCandidate);

            return GuideRemissionTicketStatusResponse.builder()
                    .ticket(text(root, "ticket"))
                    .ticketRpta(text(root, "ticket_rpta"))
                    .indCdrGenerado(text(root, "indCdrGenerado"))
                    .cdrHash(text(root, "cdr_hash"))
                    .cdrMsjSunat(text(root, "cdr_msj_sunat"))
                    .cdrResponseCode(text(root, "cdr_ResponseCode"))
                    .documentDescription(text(root, "DocumentDescription"))
                    .rutaXml(text(root, "ruta_xml"))
                    .rutaCdr(text(root, "ruta_cdr"))
                    .numerror(text(root, "numerror"))
                    .cod(intValue(root, "cod"))
                    .msg(text(root, "msg"))
                    .exc(text(root, "exc"))
                    .build();
        } catch (Exception ex) {
            throw new GuideRemissionIntegrationException(
                    "No se pudo interpretar la respuesta de consulta de ticket. status=" + raw.statusCode().value()
                            + ", contentType=" + raw.contentType()
                            + ", body=" + sanitizeForLog(responseBody), ex);
        }
    }

    private String text(com.fasterxml.jackson.databind.JsonNode root, String field) {
        if (root == null || !root.has(field) || root.get(field).isNull()) {
            return null;
        }
        return root.get(field).asText();
    }

    private Integer intValue(com.fasterxml.jackson.databind.JsonNode root, String field) {
        if (root == null || !root.has(field) || root.get(field).isNull()) {
            return null;
        }
        if (root.get(field).isInt() || root.get(field).canConvertToInt()) {
            return root.get(field).asInt();
        }
        String value = root.get(field).asText();
        if (value == null || value.isBlank()) {
            return null;
        }
        return Integer.valueOf(value);
    }

    private Long longValue(com.fasterxml.jackson.databind.JsonNode root, String field) {
        if (root == null || !root.has(field) || root.get(field).isNull()) {
            return null;
        }
        if (root.get(field).isLong() || root.get(field).canConvertToLong()) {
            return root.get(field).asLong();
        }
        String value = root.get(field).asText();
        if (value == null || value.isBlank()) {
            return null;
        }
        return Long.valueOf(value);
    }

    private String extractJsonObject(String responseBody) {
        String trimmed = responseBody.trim();
        if ((trimmed.startsWith("{") && trimmed.endsWith("}")) || (trimmed.startsWith("[") && trimmed.endsWith("]"))) {
            return trimmed;
        }

        int firstBrace = trimmed.indexOf('{');
        int lastBrace = trimmed.lastIndexOf('}');
        if (firstBrace >= 0 && lastBrace > firstBrace) {
            return trimmed.substring(firstBrace, lastBrace + 1);
        }

        return trimmed;
    }

    private String buildErrorMessage(String baseMessage, RestClientResponseException ex) {
        String responseBody = ex.getResponseBodyAsString();
        return baseMessage + ". Status=" + ex.getStatusCode() + ", body=" + sanitizeForLog(responseBody);
    }

    private String sanitizeForLog(String body) {
        if (body == null) {
            return "";
        }
        String sanitized = body
                .replaceAll("(?i)(\\\"access_token\\\"\\s*:\\s*\\\")[^\\\"]+", "$1***")
                .replaceAll("(?i)(\\\"guias_client_secret\\\"\\s*:\\s*\\\")[^\\\"]+", "$1***")
                .replaceAll("(?i)(\\\"guias_client_id\\\"\\s*:\\s*\\\")[^\\\"]+", "$1***")
                .replaceAll("(?i)(\\\"token_access\\\"\\s*:\\s*\\\")[^\\\"]+", "$1***")
                .replaceAll("(?i)(\\\"token\\\"\\s*:\\s*\\\")[^\\\"]+", "$1***")
                .replaceAll("[\\r\\n\\t]+", " ")
                .trim();
        if (sanitized.length() > MAX_LOG_BODY_LENGTH) {
            return sanitized.substring(0, MAX_LOG_BODY_LENGTH) + "...";
        }
        return sanitized;
    }

    private String maskUser(String user) {
        if (user == null || user.isBlank()) {
            return "";
        }
        if (user.length() <= 2) {
            return "**";
        }
        return user.charAt(0) + "***" + user.charAt(user.length() - 1);
    }

    private String maskTicket(String ticket) {
        if (ticket == null || ticket.isBlank()) {
            return "";
        }
        if (ticket.length() <= 8) {
            return "***";
        }
        return ticket.substring(0, 4) + "***" + ticket.substring(ticket.length() - 4);
    }

    private record RawExternalResponse(HttpStatusCode statusCode, MediaType contentType, String body) {}
}
