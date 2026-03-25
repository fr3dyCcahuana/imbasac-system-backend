package com.paulfernandosr.possystembackend.guideremission.domain;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import jakarta.validation.constraints.NotBlank;
import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class GuideRemissionItemSourceLine {
    @NotBlank
    private String relatedDocumentTypeCode;

    @NotBlank
    private String relatedDocumentSerie;

    @NotBlank
    private String relatedDocumentNumero;

    private Integer relatedDocumentLineNo;

    @NotBlank
    private String cantidad;

    private String sourceItemCode;
    private String sourceItemDescription;
}
