package com.paulfernandosr.possystembackend.manualpdf.domain;

public record ManualPdfImage(
        Long id,
        Long documentId,
        String fileName,
        String fileKey,
        String mimeType,
        Long fileSize,
        Integer sortOrder,
        Boolean enabled
) {
}
