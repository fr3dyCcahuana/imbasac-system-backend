package com.paulfernandosr.possystembackend.whatsappcenter.infrastructure.adapter.input;

import com.paulfernandosr.possystembackend.whatsappcenter.infrastructure.adapter.output.WhatsAppAgentClient;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.concurrent.TimeUnit;

@RestController
@RequestMapping("/whatsapp-center/media")
@RequiredArgsConstructor
public class WhatsAppCenterMediaController {
    private final WhatsAppAgentClient agentClient;

    @GetMapping("/{mediaId}")
    public ResponseEntity<byte[]> media(@PathVariable String mediaId) {
        ResponseEntity<byte[]> response = agentClient.media(mediaId);
        MediaType contentType = response.getHeaders().getContentType();
        if (contentType == null) {
            contentType = MediaType.APPLICATION_OCTET_STREAM;
        }
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(contentType);
        // Los binarios se persisten localmente en el AI agent, por lo que son
        // estables para una vida util larga. Cache de 24h del lado cliente.
        headers.setCacheControl(CacheControl.maxAge(24, TimeUnit.HOURS).cachePrivate());
        String disposition = response.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION);
        if (disposition != null && !disposition.isBlank()) {
            headers.set(HttpHeaders.CONTENT_DISPOSITION, disposition);
        }
        return ResponseEntity.status(response.getStatusCode())
                .headers(headers)
                .body(response.getBody());
    }
}
