package com.paulfernandosr.possystembackend.whatsapp.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.paulfernandosr.possystembackend.whatsapp.domain.WhatsAppDownloadedMedia;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class OpenAiVisionCodeExtractor {
    private final WhatsAppIntegrationProperties properties;
    private final ObjectMapper objectMapper;

    public Optional<String> extractText(WhatsAppDownloadedMedia media) {
        WhatsAppIntegrationProperties.Vision vision = properties.getVision();
        if (!vision.isEnabled() || vision.getOpenAiApiKey() == null || vision.getOpenAiApiKey().isBlank()) {
            return Optional.empty();
        }
        if (media == null || media.getContent() == null || media.getContent().length == 0) {
            return Optional.empty();
        }
        try {
            String mimeType = media.getMimeType() == null || media.getMimeType().isBlank() ? "image/jpeg" : media.getMimeType();
            String base64 = Base64.getEncoder().encodeToString(media.getContent());
            String imageUrl = "data:" + mimeType + ";base64," + base64;

            String prompt = "Lee esta imagen o documento enviado por WhatsApp. " +
                    "Extrae únicamente códigos de productos/SKU/códigos de barras y cantidades si aparecen. " +
                    "Devuelve cada item en una línea con el formato CODIGO x CANTIDAD. " +
                    "Si no ves cantidad, usa x 1. No inventes códigos. No agregues explicación.";

            Map<String, Object> payload = Map.of(
                    "model", vision.getOpenAiModel(),
                    "input", List.of(Map.of(
                            "role", "user",
                            "content", List.of(
                                    Map.of("type", "input_text", "text", prompt),
                                    Map.of("type", "input_image", "image_url", imageUrl)
                            )
                    ))
            );

            String rawResponse = RestClient.builder()
                    .baseUrl("https://api.openai.com/v1")
                    .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + vision.getOpenAiApiKey())
                    .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                    .build()
                    .post()
                    .uri("/responses")
                    .body(payload)
                    .retrieve()
                    .body(String.class);

            JsonNode json = objectMapper.readTree(rawResponse);
            String outputText = json.path("output_text").asText(null);
            if (outputText != null && !outputText.isBlank()) return Optional.of(outputText);
            return findFirstText(json);
        } catch (Exception exception) {
            log.warn("No se pudo extraer texto con visión. mediaId={}, error={}", media.getMediaId(), exception.getMessage());
            return Optional.empty();
        }
    }

    private Optional<String> findFirstText(JsonNode root) {
        if (root == null || root.isMissingNode()) return Optional.empty();
        JsonNode output = root.path("output");
        if (!output.isArray()) return Optional.empty();
        for (JsonNode item : output) {
            JsonNode content = item.path("content");
            if (!content.isArray()) continue;
            for (JsonNode c : content) {
                String text = c.path("text").asText(null);
                if (text != null && !text.isBlank()) return Optional.of(text);
            }
        }
        return Optional.empty();
    }
}
