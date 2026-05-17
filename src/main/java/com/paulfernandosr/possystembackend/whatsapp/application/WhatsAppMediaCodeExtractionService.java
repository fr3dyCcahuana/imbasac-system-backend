package com.paulfernandosr.possystembackend.whatsapp.application;

import com.paulfernandosr.possystembackend.whatsapp.domain.WhatsAppBatchCodeLine;
import com.paulfernandosr.possystembackend.whatsapp.domain.WhatsAppDownloadedMedia;
import com.paulfernandosr.possystembackend.whatsapp.domain.port.output.WhatsAppMediaGateway;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class WhatsAppMediaCodeExtractionService {
    private final WhatsAppIntegrationProperties properties;
    private final WhatsAppMediaGateway mediaGateway;
    private final OpenAiVisionCodeExtractor openAiVisionCodeExtractor;
    private final WhatsAppBatchCodeExtractorService codeExtractorService;

    public MediaExtractionResult extractCodesFromMedia(String mediaId, String fallbackMimeType) {
        if (mediaId == null || mediaId.isBlank()) {
            return MediaExtractionResult.disabled("No se recibió identificador de imagen/documento.");
        }
        if (!properties.getVision().isEnabled()) {
            return MediaExtractionResult.disabled("La lectura automática de imágenes está desactivada.");
        }

        Optional<WhatsAppDownloadedMedia> mediaOpt = mediaGateway.downloadMedia(mediaId);
        if (mediaOpt.isEmpty()) {
            return MediaExtractionResult.disabled("No se pudo descargar la imagen/documento de WhatsApp.");
        }

        WhatsAppDownloadedMedia media = mediaOpt.get();
        if ((media.getMimeType() == null || media.getMimeType().isBlank()) && fallbackMimeType != null) {
            media.setMimeType(fallbackMimeType);
        }
        String mimeType = media.getMimeType() == null ? "" : media.getMimeType().toLowerCase();
        if (!mimeType.startsWith("image/")) {
            return MediaExtractionResult.disabled("Por ahora la lectura automática funciona con fotos o imágenes. Si es PDF/Excel, copia los códigos en texto o envía captura/foto clara.");
        }
        if (media.sizeBytes() > properties.getVision().getMaxMediaBytes()) {
            return MediaExtractionResult.disabled("La imagen/documento excede el tamaño máximo configurado para lectura automática.");
        }

        Optional<String> textOpt = openAiVisionCodeExtractor.extractText(media);
        if (textOpt.isEmpty() || textOpt.get().isBlank()) {
            return MediaExtractionResult.enabled(List.of(), "");
        }
        String extractedText = textOpt.get();
        List<WhatsAppBatchCodeLine> codes = codeExtractorService.extractCodes(extractedText);
        return MediaExtractionResult.enabled(codes, extractedText);
    }

    public record MediaExtractionResult(boolean enabled, List<WhatsAppBatchCodeLine> codes, String extractedText, String message) {
        public static MediaExtractionResult disabled(String message) {
            return new MediaExtractionResult(false, List.of(), "", message);
        }
        public static MediaExtractionResult enabled(List<WhatsAppBatchCodeLine> codes, String extractedText) {
            return new MediaExtractionResult(true, codes == null ? List.of() : codes, extractedText, null);
        }
    }
}
