package com.paulfernandosr.possystembackend.manualpdf.domain;

public record ManualPdfModel(
        Long id,
        Long familyId,
        String code,
        String name,
        Integer sortOrder
) {
}
