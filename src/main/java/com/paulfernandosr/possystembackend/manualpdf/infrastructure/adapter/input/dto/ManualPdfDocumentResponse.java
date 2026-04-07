package com.paulfernandosr.possystembackend.manualpdf.infrastructure.adapter.input.dto;

public record ManualPdfDocumentResponse(
        Long id,
        String title,
        String fileName,
        Integer yearFrom,
        Integer yearTo,
        Boolean enabled,
        String previewUrl,
        String downloadUrl
) {
}
