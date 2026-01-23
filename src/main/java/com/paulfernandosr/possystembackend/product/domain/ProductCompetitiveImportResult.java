package com.paulfernandosr.possystembackend.product.domain;

import lombok.*;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductCompetitiveImportResult {

    private Boolean dryRun;
    private Boolean atomic;
    private BigDecimal minPrice;

    @Builder.Default
    private List<ProductCompetitiveImportError> errors = new ArrayList<>();

    @Builder.Default
    private List<ProductCompetitiveImportPreviewRow> preview = new ArrayList<>();

    @Builder.Default
    private ProductCompetitiveImportSummary summary = ProductCompetitiveImportSummary.empty();

    public boolean hasErrors() {
        return errors != null && !errors.isEmpty();
    }

    public void addError(ProductCompetitiveImportError e) {
        if (errors == null) errors = new ArrayList<>();
        errors.add(e);
    }

    public void computeSummary() {
        if (summary == null) summary = ProductCompetitiveImportSummary.empty();
        summary.setErrorsCount(errors == null ? 0 : errors.size());
    }
}
