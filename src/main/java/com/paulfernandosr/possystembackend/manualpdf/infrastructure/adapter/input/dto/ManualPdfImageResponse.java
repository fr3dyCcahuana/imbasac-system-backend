package com.paulfernandosr.possystembackend.manualpdf.infrastructure.adapter.input.dto;

public record ManualPdfImageResponse(
        Long id,
        String fileName,
        String imageUrl,
        Integer sortOrder
) {
}
