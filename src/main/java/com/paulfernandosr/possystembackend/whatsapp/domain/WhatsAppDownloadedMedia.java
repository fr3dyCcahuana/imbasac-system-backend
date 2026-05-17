package com.paulfernandosr.possystembackend.whatsapp.domain;

import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WhatsAppDownloadedMedia {
    private String mediaId;
    private String mimeType;
    private String sha256;
    private String filename;
    private byte[] content;

    public int sizeBytes() {
        return content == null ? 0 : content.length;
    }
}
