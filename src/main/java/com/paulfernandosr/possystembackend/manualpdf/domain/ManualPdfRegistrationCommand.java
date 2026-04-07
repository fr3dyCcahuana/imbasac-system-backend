package com.paulfernandosr.possystembackend.manualpdf.domain;

public record ManualPdfRegistrationCommand(
        String familyCode,
        String familyName,
        Integer familySortOrder,
        String modelCode,
        String modelName,
        Integer modelSortOrder,
        String title,
        Integer yearFrom,
        Integer yearTo,
        Boolean enabled
) {
}
