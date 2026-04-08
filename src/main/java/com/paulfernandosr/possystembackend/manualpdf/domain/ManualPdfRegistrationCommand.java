package com.paulfernandosr.possystembackend.manualpdf.domain;

public record ManualPdfRegistrationCommand(
        Long modelId,
        String title,
        Integer yearFrom,
        Integer yearTo,
        Boolean enabled
) {
}
