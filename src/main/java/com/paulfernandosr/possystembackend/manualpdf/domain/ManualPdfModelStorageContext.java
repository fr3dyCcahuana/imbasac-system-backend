package com.paulfernandosr.possystembackend.manualpdf.domain;

public record ManualPdfModelStorageContext(
        Long modelId,
        Long familyId,
        String familyCode,
        String familyName,
        String modelCode,
        String modelName,
        Boolean familyEnabled,
        Boolean modelEnabled
) {
}
