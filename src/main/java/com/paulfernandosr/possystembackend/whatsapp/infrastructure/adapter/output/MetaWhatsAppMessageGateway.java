package com.paulfernandosr.possystembackend.whatsapp.infrastructure.adapter.output;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.paulfernandosr.possystembackend.whatsapp.application.WhatsAppIntegrationProperties;
import com.paulfernandosr.possystembackend.whatsapp.domain.WhatsAppMessageSendResult;
import com.paulfernandosr.possystembackend.whatsapp.domain.port.output.WhatsAppMessageGateway;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

import static org.springframework.http.HttpStatus.BAD_GATEWAY;

@Component
@RequiredArgsConstructor
public class MetaWhatsAppMessageGateway implements WhatsAppMessageGateway {
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
        return RestClient.builder()
                .baseUrl("https://graph.facebook.com/" + properties.getApiVersion())
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + properties.getAccessToken())
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build();
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
        Map<String, Object> payload = Map.of(
                "messaging_product", "whatsapp",
                "to", normalizeWaId(toWaId),
                "type", "template",
                "template", Map.of(
                        "name", templateName,
                        "language", Map.of("code", languageCode)
                )
        );
        return postMessage(payload);
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

    private WhatsAppMessageSendResult postMessage(Map<String, Object> payload) {
        try {
            String endpoint = "/" + properties.getPhoneNumberId() + "/messages";
            String rawResponse = restClient().post()
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
        } catch (Exception exception) {
            throw new ResponseStatusException(BAD_GATEWAY, "No se pudo enviar mensaje por WhatsApp Cloud API: " + exception.getMessage(), exception);
        }
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
}
