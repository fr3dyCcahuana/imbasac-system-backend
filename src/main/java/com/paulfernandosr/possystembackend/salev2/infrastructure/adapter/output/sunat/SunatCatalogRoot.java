package com.paulfernandosr.possystembackend.salev2.infrastructure.adapter.output.sunat;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record SunatCatalogRoot(
        Metadata metadata,
        List<CategoryDefaultEntry> categoryDefaults,
        List<RuleEntry> rules,
        List<ProductEntry> products
) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Metadata(
            String catalogType,
            String generatedAt,
            Integer sourceProducts,
            Integer sourceCategories,
            String strategy,
            List<String> notes
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record CategoryDefaultEntry(
            Long categoryId,
            String categoryName,
            String categoryNormalized,
            String defaultCode,
            String defaultLabel
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record RuleEntry(
            String id,
            List<String> categories,
            List<String> keywords,
            String code,
            String label,
            Double confidence
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ProductEntry(
            Long productId,
            Long categoryId,
            String productName,
            String normalizedName,
            String categoryName,
            String categoryNormalized,
            List<String> tokens,
            String suggestedSunatCode,
            String suggestedLabel,
            Double confidence,
            String strategy,
            String matchedRuleId
    ) {
    }
}