package com.paulfernandosr.possystembackend.guideremission.domain;

import lombok.*;

import java.math.BigDecimal;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GuideRemissionDocumentItem {
    private Integer lineNo;
    private BigDecimal quantity;
    private String description;
    private String itemCode;
    private String unitCode;
}
