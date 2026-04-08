package com.paulfernandosr.possystembackend.manualpdf.infrastructure.adapter.input.dto;

public record ManualPdfModelResponse(
        Long id,
        Long familyId,
        String code,
        String name
) {
}
