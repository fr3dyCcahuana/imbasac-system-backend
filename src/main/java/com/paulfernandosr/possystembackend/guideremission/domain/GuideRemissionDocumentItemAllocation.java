package com.paulfernandosr.possystembackend.guideremission.domain;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

import lombok.*;

import java.math.BigDecimal;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class GuideRemissionDocumentItemAllocation {
    private Integer allocationLineNo;
    private String relatedDocumentTypeCode;
    private String relatedDocumentSerie;
    private String relatedDocumentNumero;
    private Integer relatedDocumentLineNo;
    private String sourceItemCode;
    private String sourceItemDescription;
    private BigDecimal quantity;
}
