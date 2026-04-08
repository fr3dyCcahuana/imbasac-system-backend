package com.paulfernandosr.possystembackend.manualpdf.domain;

public record ManualPdfFamilyUpsertCommand(
        String code,
        String name,
        Integer sortOrder,
        Boolean enabled
) {
}
