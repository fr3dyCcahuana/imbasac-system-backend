package com.paulfernandosr.possystembackend.whatsapp.domain.port.output;

import com.paulfernandosr.possystembackend.whatsapp.domain.WhatsAppDownloadedMedia;

import java.util.Optional;

public interface WhatsAppMediaGateway {
    Optional<WhatsAppDownloadedMedia> downloadMedia(String mediaId);
}
