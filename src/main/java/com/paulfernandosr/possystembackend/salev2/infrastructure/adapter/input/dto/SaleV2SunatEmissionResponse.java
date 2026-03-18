package com.paulfernandosr.possystembackend.salev2.infrastructure.adapter.input.dto;

import lombok.*;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SaleV2SunatEmissionResponse {
    private Long saleId;
    private String docType;
    private String series;
    private Long number;
    private String sunatStatus;
    private String sunatCode;
    private String sunatDescription;
    private String hashCode;
    private String xmlPath;
    private String cdrPath;
    private String pdfPath;
    private LocalDateTime emittedAt;
}
