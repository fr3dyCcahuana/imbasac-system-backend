package com.paulfernandosr.possystembackend.manualpdf.domain;

import org.springframework.core.io.Resource;

public record ManualPdfFile(
        String fileName,
        String mimeType,
        long contentLength,
        Resource resource
) {
}
