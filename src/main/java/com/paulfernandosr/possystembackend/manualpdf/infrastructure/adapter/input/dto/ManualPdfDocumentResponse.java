package com.paulfernandosr.possystembackend.manualpdf.infrastructure.adapter.input.dto;

import java.util.List;

public record ManualPdfDocumentResponse(
        Long id,
        Long modelId,
        String title,
        String fileName,
        Integer yearFrom,
        Integer yearTo,
        String previewUrl,
        String downloadUrl,
        List<ManualPdfImageResponse> images
) {
}
