package com.paulfernandosr.possystembackend.manualpdf.domain;

public record ManualPdfModelUpsertCommand(
        Long familyId,
        String code,
        String name,
        Integer sortOrder,
        Boolean enabled
) {
}
