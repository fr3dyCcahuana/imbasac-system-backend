package com.paulfernandosr.possystembackend.whatsapp.infrastructure.adapter.output;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.paulfernandosr.possystembackend.whatsapp.application.WhatsAppIntegrationProperties;
import com.paulfernandosr.possystembackend.whatsapp.domain.WhatsAppCloudApiAuthException;
import com.paulfernandosr.possystembackend.whatsapp.domain.WhatsAppCloudApiStatus;
import com.paulfernandosr.possystembackend.whatsapp.domain.WhatsAppMessageSendResult;
import com.paulfernandosr.possystembackend.whatsapp.domain.port.output.WhatsAppCloudApiHealthGateway;
import com.paulfernandosr.possystembackend.whatsapp.domain.port.output.WhatsAppMessageGateway;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static org.springframework.http.HttpStatus.BAD_GATEWAY;

@Component
@RequiredArgsConstructor
public class MetaWhatsAppMessageGateway implements WhatsAppMessageGateway, WhatsAppCloudApiHealthGateway {
    private final WhatsAppIntegrationProperties properties;
    private final ObjectMapper objectMapper;

    @Getter
    @AllArgsConstructor
    public static class InteractiveButton {
        private String id;
        private String title;
    }

    @Getter
    @AllArgsConstructor
    public static class InteractiveListRow {
        private String id;
        private String title;
        private String description;
    }

    private RestClient restClient() {
        return restClient(currentAccessToken());
    }

    private RestClient restClient(String accessToken) {
        return RestClient.builder()
                .baseUrl("https://graph.facebook.com/" + properties.getApiVersion())
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

    private String currentAccessToken() {
        String token = properties.getAccessToken();
        return token == null ? "" : token.trim();
    }

    @Override
    public WhatsAppCloudApiStatus checkConnection() {
        try {
            String rawResponse = restClient().get()
                    .uri("/" + properties.getPhoneNumberId() + "?fields=id,display_phone_number,verified_name")
                    .retrieve()
                    .body(String.class);

            JsonNode json = objectMapper.readTree(rawResponse);
            return WhatsAppCloudApiStatus.builder()
                    .ok(true)
                    .apiVersion(properties.getApiVersion())
                    .phoneNumberId(json.path("id").asText(properties.getPhoneNumberId()))
                    .displayPhoneNumber(json.path("display_phone_number").asText(null))
                    .verifiedName(json.path("verified_name").asText(null))
                    .message("Conexión correcta con WhatsApp Cloud API.")
                    .build();
        } catch (RestClientResponseException exception) {
            MetaError error = parseMetaError(exception.getResponseBodyAsString());
            return WhatsAppCloudApiStatus.builder()
                    .ok(false)
                    .apiVersion(properties.getApiVersion())
                    .phoneNumberId(properties.getPhoneNumberId())
                    .message(toFriendlyMetaError(error, exception))
                    .errorCode(error.code())
                    .errorSubcode(error.subcode())
                    .fbtraceId(error.fbtraceId())
                    .build();
        } catch (Exception exception) {
            return WhatsAppCloudApiStatus.builder()
                    .ok(false)
                    .apiVersion(properties.getApiVersion())
                    .phoneNumberId(properties.getPhoneNumberId())
                    .message("No se pudo validar la conexión con WhatsApp Cloud API: " + exception.getMessage())
                    .build();
        }
    }

    @Override
    public WhatsAppMessageSendResult sendText(String toWaId, String body) {
        Map<String, Object> payload = Map.of(
                "messaging_product", "whatsapp",
                "to", normalizeWaId(toWaId),
                "type", "text",
                "text", Map.of("preview_url", false, "body", body)
        );
        return postMessage(payload);
    }

    @Override
    public WhatsAppMessageSendResult sendTemplate(String toWaId, String templateName, String languageCode) {
        return sendTemplateWithImage(toWaId, templateName, languageCode, null, List.of());
    }

    @Override
    public WhatsAppMessageSendResult sendDocumentByLink(String toWaId, String documentUrl, String filename, String caption) {
        Map<String, Object> document = Map.of(
                "link", documentUrl,
                "filename", filename,
                "caption", caption == null ? "" : caption
        );
        Map<String, Object> payload = Map.of(
                "messaging_product", "whatsapp",
                "to", normalizeWaId(toWaId),
                "type", "document",
                "document", document
        );
        return postMessage(payload);
    }

    @Override
    public WhatsAppMessageSendResult sendButtons(String toWaId, String body, List<InteractiveButton> buttons) {
        List<Map<String, Object>> buttonPayload = buttons.stream()
                .limit(3)
                .map(button -> Map.<String, Object>of(
                        "type", "reply",
                        "reply", Map.of(
                                "id", safeId(button.getId()),
                                "title", safeTitle(button.getTitle(), 20)
                        )
                ))
                .toList();

        Map<String, Object> payload = Map.of(
                "messaging_product", "whatsapp",
                "to", normalizeWaId(toWaId),
                "type", "interactive",
                "interactive", Map.of(
                        "type", "button",
                        "body", Map.of("text", safeBody(body)),
                        "action", Map.of("buttons", buttonPayload)
                )
        );
        return postMessage(payload);
    }

    @Override
    public WhatsAppMessageSendResult sendList(String toWaId, String body, String buttonText, String sectionTitle, List<InteractiveListRow> rows) {
        List<Map<String, Object>> rowPayload = rows.stream()
                .limit(10)
                .map(row -> Map.<String, Object>of(
                        "id", safeId(row.getId()),
                        "title", safeTitle(row.getTitle(), 24),
                        "description", safeDescription(row.getDescription())
                ))
                .toList();

        Map<String, Object> payload = Map.of(
                "messaging_product", "whatsapp",
                "to", normalizeWaId(toWaId),
                "type", "interactive",
                "interactive", Map.of(
                        "type", "list",
                        "body", Map.of("text", safeBody(body)),
                        "action", Map.of(
                                "button", safeTitle(buttonText == null ? "Ver opciones" : buttonText, 20),
                                "sections", List.of(Map.of(
                                        "title", safeTitle(sectionTitle == null ? "Opciones" : sectionTitle, 24),
                                        "rows", rowPayload
                                ))
                        )
                )
        );
        return postMessage(payload);
    }

    @Override
    public WhatsAppMessageSendResult sendTemplateWithImage(String toWaId,
                                                           String templateName,
                                                           String languageCode,
                                                           String imageUrl,
                                                           List<String> bodyParameters) {
        Map<String, Object> template = new LinkedHashMap<>();
        template.put("name", safeTemplateName(templateName));
        template.put("language", Map.of("code", normalizeLanguageCode(languageCode)));

        List<Map<String, Object>> components = new ArrayList<>();
        if (imageUrl != null && !imageUrl.isBlank()) {
            components.add(Map.of(
                    "type", "header",
                    "parameters", List.of(Map.of(
                            "type", "image",
                            "image", Map.of("link", imageUrl.trim())
                    ))
            ));
        }

        List<Map<String, Object>> bodyParams = normalizeBodyParameters(bodyParameters);
        if (!bodyParams.isEmpty()) {
            components.add(Map.of(
                    "type", "body",
                    "parameters", bodyParams
            ));
        }

        if (!components.isEmpty()) {
            template.put("components", components);
        }

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("messaging_product", "whatsapp");
        payload.put("to", normalizeWaId(toWaId));
        payload.put("type", "template");
        payload.put("template", template);

        return postMessage(payload);
    }

    private WhatsAppMessageSendResult postMessage(Map<String, Object> payload) {
        String endpoint = "/" + properties.getPhoneNumberId() + "/messages";
        String firstToken = currentAccessToken();

        try {
            return doPostMessage(endpoint, payload, firstToken);
        } catch (RestClientResponseException exception) {
            MetaError error = parseMetaError(exception.getResponseBodyAsString());

            if (isAuthError(error, exception)) {
                String refreshedToken = currentAccessToken();
                if (!sameToken(firstToken, refreshedToken)) {
                    try {
                        return doPostMessage(endpoint, payload, refreshedToken);
                    } catch (RestClientResponseException retryException) {
                        MetaError retryError = parseMetaError(retryException.getResponseBodyAsString());
                        if (isAuthError(retryError, retryException)) {
                            throw authException(retryError, retryException);
                        }
                        throw new ResponseStatusException(BAD_GATEWAY, toFriendlyMetaError(retryError, retryException), retryException);
                    } catch (Exception retryException) {
                        throw new ResponseStatusException(BAD_GATEWAY, "No se pudo enviar mensaje por WhatsApp Cloud API después de reintentar con token actualizado: " + retryException.getMessage(), retryException);
                    }
                }

                throw authException(error, exception);
            }

            throw new ResponseStatusException(BAD_GATEWAY, toFriendlyMetaError(error, exception), exception);
        } catch (Exception exception) {
            throw new ResponseStatusException(BAD_GATEWAY, "No se pudo enviar mensaje por WhatsApp Cloud API: " + exception.getMessage(), exception);
        }
    }

    private WhatsAppMessageSendResult doPostMessage(String endpoint, Map<String, Object> payload, String accessToken) throws Exception {
        String rawResponse = restClient(accessToken).post()
                .uri(endpoint)
                .body(payload)
                .retrieve()
                .body(String.class);

        JsonNode json = objectMapper.readTree(rawResponse);
        JsonNode message = json.path("messages").isArray() && json.path("messages").size() > 0
                ? json.path("messages").get(0)
                : objectMapper.createObjectNode();
        return WhatsAppMessageSendResult.builder()
                .waMessageId(message.path("id").asText(null))
                .status(message.path("message_status").asText("accepted"))
                .rawResponse(rawResponse)
                .build();
    }

    private WhatsAppCloudApiAuthException authException(MetaError error, RestClientResponseException exception) {
        return new WhatsAppCloudApiAuthException(
                toFriendlyMetaError(error, exception),
                error.code(),
                error.subcode(),
                error.fbtraceId()
        );
    }

    private boolean isAuthError(MetaError error, RestClientResponseException exception) {
        return exception.getStatusCode().value() == 401 || "190".equals(error.code());
    }

    private boolean sameToken(String firstToken, String refreshedToken) {
        return Objects.equals(firstToken == null ? "" : firstToken.trim(), refreshedToken == null ? "" : refreshedToken.trim());
    }

    private MetaError parseMetaError(String rawBody) {
        try {
            JsonNode error = objectMapper.readTree(rawBody).path("error");
            String details = error.path("error_data").path("details").asText(null);
            return new MetaError(
                    error.path("message").asText(null),
                    error.path("type").asText(null),
                    error.path("code").asText(null),
                    error.path("error_subcode").asText(null),
                    details,
                    error.path("fbtrace_id").asText(null),
                    rawBody
            );
        } catch (Exception ignored) {
            return new MetaError(null, null, null, null, null, null, rawBody);
        }
    }

    private String toFriendlyMetaError(MetaError error, RestClientResponseException exception) {
        if ("190".equals(error.code())) {
            return "Token de WhatsApp Cloud API inválido, vencido o revocado. Actualiza whatsapp.access-token con un token vigente y reinicia el backend. Detalle Meta: " + safeMetaMessage(error, exception);
        }
        if ("132000".equals(error.code())) {
            return "La cantidad de parámetros enviados no coincide con la plantilla aprobada en Meta. Revisa bodyParameters y el texto registrado de la plantilla. Detalle Meta: " + safeMetaMessage(error, exception);
        }
        if ("132001".equals(error.code())) {
            return "Meta rechazó el envío porque la plantilla no existe o no está aprobada para ese idioma. Revisa templateName y languageCode. Detalle Meta: " + safeMetaMessage(error, exception);
        }
        if (exception.getStatusCode().value() == 401) {
            return "WhatsApp Cloud API respondió 401 Unauthorized. Revisa el access token configurado en el backend. Detalle Meta: " + safeMetaMessage(error, exception);
        }
        return "No se pudo enviar mensaje por WhatsApp Cloud API: " + safeMetaMessage(error, exception);
    }

    private String safeMetaMessage(MetaError error, RestClientResponseException exception) {
        if (error.details() != null && !error.details().isBlank()) return error.details();
        if (error.message() != null && !error.message().isBlank()) return error.message();
        String raw = exception.getResponseBodyAsString();
        return raw == null || raw.isBlank() ? exception.getMessage() : raw;
    }

    private String safeTemplateName(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("El nombre de la plantilla WhatsApp es obligatorio.");
        }
        return value.trim();
    }

    private String normalizeLanguageCode(String value) {
        return value == null || value.isBlank() ? "es" : value.trim();
    }

    private List<Map<String, Object>> normalizeBodyParameters(List<String> bodyParameters) {
        if (bodyParameters == null || bodyParameters.isEmpty()) {
            return List.of();
        }
        return bodyParameters.stream()
                .map(value -> Map.<String, Object>of(
                        "type", "text",
                        "text", value == null ? "" : value
                ))
                .toList();
    }

    private String normalizeWaId(String value) {
        return value == null ? null : value.replace("+", "").replace(" ", "").replace("-", "");
    }

    private String safeId(String value) {
        String id = value == null || value.isBlank() ? "option" : value;
        return id.length() > 200 ? id.substring(0, 200) : id;
    }

    private String safeTitle(String value, int max) {
        String title = value == null || value.isBlank() ? "Opción" : value.trim();
        return title.length() > max ? title.substring(0, max) : title;
    }

    private String safeDescription(String value) {
        if (value == null) return "";
        String description = value.trim();
        return description.length() > 72 ? description.substring(0, 72) : description;
    }

    private String safeBody(String value) {
        String body = value == null || value.isBlank() ? "Selecciona una opción." : value.trim();
        return body.length() > 1000 ? body.substring(0, 1000) : body;
    }

    private record MetaError(String message,
                             String type,
                             String code,
                             String subcode,
                             String details,
                             String fbtraceId,
                             String rawBody) {
    }
}
