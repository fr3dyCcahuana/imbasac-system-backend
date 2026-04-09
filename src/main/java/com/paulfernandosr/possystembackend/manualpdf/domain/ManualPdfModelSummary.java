package com.paulfernandosr.possystembackend.manualpdf.domain;

public record ManualPdfModelSummary(
        Long modelId,
        String modelCode,
        String modelName,
        Long familyId,
        String familyCode,
        String familyName
) {
}
