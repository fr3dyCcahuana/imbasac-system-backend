package com.paulfernandosr.possystembackend.product.application.importer;

import lombok.*;

import java.util.Set;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CompetitiveImportWorkbook {
    private Set<String> allowedCategories;
    private Set<String> allowedPresentations;
    private Set<String> allowedOriginTypes;
    private Set<String> allowedCountries;

    private java.util.List<CompetitiveImportRow> rows;
}
