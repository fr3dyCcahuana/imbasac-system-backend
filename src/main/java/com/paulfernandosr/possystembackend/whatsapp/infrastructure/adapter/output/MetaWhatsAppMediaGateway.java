package com.paulfernandosr.possystembackend.whatsapp.infrastructure.adapter.output;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.paulfernandosr.possystembackend.whatsapp.application.WhatsAppIntegrationProperties;
import com.paulfernandosr.possystembackend.whatsapp.domain.WhatsAppDownloadedMedia;
import com.paulfernandosr.possystembackend.whatsapp.domain.port.output.WhatsAppMediaGateway;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Optional;

@Slf4j
@Component
@RequiredArgsConstructor
public class MetaWhatsAppMediaGateway implements WhatsAppMediaGateway {
    private final WhatsAppIntegrationProperties properties;
    private final ObjectMapper objectMapper;

    @Override
    public Optional<WhatsAppDownloadedMedia> downloadMedia(String mediaId) {
        if (mediaId == null || mediaId.isBlank()) return Optional.empty();
        try {
            RestClient graph = RestClient.builder()
                    .baseUrl("https://graph.facebook.com/" + properties.getApiVersion())
                    .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + properties.getAccessToken())
                    .build();

            String rawInfo = graph.get()
                    .uri("/" + mediaId)
                    .retrieve()
                    .body(String.class);
            JsonNode info = objectMapper.readTree(rawInfo);
            String url = info.path("url").asText(null);
            if (url == null || url.isBlank()) return Optional.empty();

            byte[] bytes = RestClient.builder()
                    .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + properties.getAccessToken())
                    .build()
                    .get()
                    .uri(url)
                    .retrieve()
                    .body(byte[].class);

            return Optional.of(WhatsAppDownloadedMedia.builder()
                    .mediaId(mediaId)
                    .mimeType(info.path("mime_type").asText(null))
                    .sha256(info.path("sha256").asText(null))
                    .filename(info.path("file_name").asText(null))
                    .content(bytes)
                    .build());
        } catch (Exception exception) {
            log.warn("No se pudo descargar media de WhatsApp. mediaId={}, error={}", mediaId, exception.getMessage());
            return Optional.empty();
        }
    }
}
