package com.paulfernandosr.possystembackend.manualpdf.infrastructure.adapter.input.dto;

public record CreateManualPdfModelRequest(
        Long familyId,
        String code,
        String name,
        Integer sortOrder
) {
}
