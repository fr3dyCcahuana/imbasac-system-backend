package com.paulfernandosr.possystembackend.manualpdf.domain;

public record ManualPdfStoredFile(
        String fileKey,
        String fileName,
        String mimeType,
        Long fileSize
) {
}
