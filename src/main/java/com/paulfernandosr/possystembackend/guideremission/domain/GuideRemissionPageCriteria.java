package com.paulfernandosr.possystembackend.guideremission.domain;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.*;

import java.time.LocalDate;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class GuideRemissionPageCriteria {
    @Builder.Default
    private Integer page = 0;

    @Builder.Default
    private Integer size = 10;

    private String query;
    private String status;
    private String serie;
    private String numero;
    private String recipientDocumentNumber;
    private String relatedDocument;
    private LocalDate issueDateFrom;
    private LocalDate issueDateTo;
    private LocalDate transferDateFrom;
    private LocalDate transferDateTo;
}
