package com.paulfernandosr.possystembackend.product.infrastructure.adapter.input.dto;

import com.paulfernandosr.possystembackend.product.domain.ProductCompetitiveImportError;
import com.paulfernandosr.possystembackend.product.domain.ProductCompetitiveImportResult;
import lombok.*;

import java.util.List;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CompetitiveImportErrorResponse {
    private boolean hasErrors;
    private List<ProductCompetitiveImportError> errors;

    public static CompetitiveImportErrorResponse from(ProductCompetitiveImportResult result) {
        return CompetitiveImportErrorResponse.builder()
                .hasErrors(true)
                .errors(result.getErrors())
                .build();
    }
}
