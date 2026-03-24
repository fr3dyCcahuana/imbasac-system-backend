package com.paulfernandosr.possystembackend.guideremission.domain;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.util.List;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class GuideRemissionFullFlowRequest {
    @Valid
    @NotNull
    private GuideRemissionData guia;

    @Valid
    @NotEmpty
    private List<GuideRemissionItem> items;

    private String relatedDocumentTypeCode;
    private String relatedDocumentSerie;
    private String relatedDocumentNumero;
}
