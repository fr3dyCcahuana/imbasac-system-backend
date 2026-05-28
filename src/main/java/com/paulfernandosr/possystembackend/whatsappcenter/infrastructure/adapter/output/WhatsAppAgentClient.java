package com.paulfernandosr.possystembackend.whatsappcenter.infrastructure.adapter.output;

import com.fasterxml.jackson.databind.JsonNode;
import com.paulfernandosr.possystembackend.whatsappcenter.infrastructure.adapter.input.dto.WhatsAppCenterDtos.ManualModeRequest;
import com.paulfernandosr.possystembackend.whatsappcenter.infrastructure.adapter.input.dto.WhatsAppCenterDtos.SendManualMessageRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.http.MediaType;
import org.springframework.util.MultiValueMap;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.multipart.MultipartFile;

@Component
public class WhatsAppAgentClient {
    private final RestClient restClient;

    public WhatsAppAgentClient(@Value("${app.whatsapp-agent.base-url:http://127.0.0.1:8092}") String baseUrl) {
        this.restClient = RestClient.builder()
                .baseUrl(trimTrailingSlash(baseUrl))
                .build();
    }

    public JsonNode setManualMode(Long conversationId, ManualModeRequest request) {
        try {
            JsonNode response = restClient.post()
                    .uri("/whatsapp/conversations/{conversationId}/manual-mode", conversationId)
                    .body(request)
                    .retrieve()
                    .body(JsonNode.class);
            return payload(response);
        } catch (RestClientResponseException ex) {
            throw translate(ex);
        }
    }

    public JsonNode sendText(Long conversationId, SendManualMessageRequest request) {
        try {
            JsonNode response = restClient.post()
                    .uri("/whatsapp/conversations/{conversationId}/messages/text", conversationId)
                    .body(request)
                    .retrieve()
                    .body(JsonNode.class);
            return payload(response);
        } catch (RestClientResponseException ex) {
            throw translate(ex);
        }
    }

    public JsonNode sendMedia(Long conversationId, MultipartFile file, String caption) {
        try {
            MultipartBodyBuilder bodyBuilder = new MultipartBodyBuilder();
            MediaType contentType = MediaType.APPLICATION_OCTET_STREAM;
            if (file.getContentType() != null && !file.getContentType().isBlank()) {
                contentType = MediaType.parseMediaType(file.getContentType());
            }
            bodyBuilder.part("file", file.getResource())
                    .filename(file.getOriginalFilename() == null ? "archivo" : file.getOriginalFilename())
                    .contentType(contentType);
            bodyBuilder.part("caption", caption == null ? "" : caption);
            MultiValueMap<String, org.springframework.http.HttpEntity<?>> body = bodyBuilder.build();
            JsonNode response = restClient.post()
                    .uri("/whatsapp/conversations/{conversationId}/messages/media", conversationId)
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(body)
                    .retrieve()
                    .body(JsonNode.class);
            return payload(response);
        } catch (RestClientResponseException ex) {
            throw translate(ex);
        }
    }

    public ResponseEntity<byte[]> media(String mediaId) {
        return restClient.get()
                .uri("/whatsapp/media/{mediaId}", mediaId)
                .retrieve()
                .toEntity(byte[].class);
    }

    private JsonNode payload(JsonNode response) {
        if (response == null) {
            return null;
        }
        return response.has("payload") ? response.get("payload") : response;
    }

    private ResponseStatusException translate(RestClientResponseException ex) {
        return new ResponseStatusException(ex.getStatusCode(), ex.getResponseBodyAsString(), ex);
    }

    private String trimTrailingSlash(String value) {
        String text = value == null || value.isBlank() ? "http://127.0.0.1:8092" : value.trim();
        while (text.endsWith("/")) {
            text = text.substring(0, text.length() - 1);
        }
        return text;
    }
}
