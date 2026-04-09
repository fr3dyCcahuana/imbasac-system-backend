package com.paulfernandosr.possystembackend.manualpdf.infrastructure.adapter.input.dto;

public record CreateManualPdfFamilyRequest(
        String code,
        String name,
        Integer sortOrder
) {
}
