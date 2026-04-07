package com.paulfernandosr.possystembackend.manualpdf.domain;

public record ManualPdfDocument(
        Long id,
        Long modelId,
        String title,
        Integer yearFrom,
        Integer yearTo,
        String fileName,
        String fileKey,
        String mimeType,
        Long fileSize,
        Boolean enabled
) {
}
