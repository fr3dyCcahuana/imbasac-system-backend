package com.paulfernandosr.possystembackend.manualpdf.infrastructure.adapter.input;

import org.springframework.stereotype.Component;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@Component
public class ManualPdfPublicUrlService {

    public String buildPreviewUrl(Long documentId) {
        return ServletUriComponentsBuilder
                .fromCurrentContextPath()
                .path("/manual-pdfs/" + documentId + "/preview")
                .toUriString();
    }

    public String buildDownloadUrl(Long documentId) {
        return ServletUriComponentsBuilder
                .fromCurrentContextPath()
                .path("/manual-pdfs/" + documentId + "/download")
                .toUriString();
    }
}
