package com.paulfernandosr.possystembackend.manualpdf.domain;

public record ManualPdfFamily(
        Long id,
        String code,
        String name,
        Integer sortOrder
) {
}
