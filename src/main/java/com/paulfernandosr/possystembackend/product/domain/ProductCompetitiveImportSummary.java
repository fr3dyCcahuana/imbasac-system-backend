package com.paulfernandosr.possystembackend.product.domain;

import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductCompetitiveImportSummary {

    private int rowsRead;
    private int validRows;
    private int errorsCount;

    private int inserted;
    private int updated;

    public static ProductCompetitiveImportSummary empty() {
        return ProductCompetitiveImportSummary.builder()
                .rowsRead(0)
                .validRows(0)
                .errorsCount(0)
                .inserted(0)
                .updated(0)
                .build();
    }
}
