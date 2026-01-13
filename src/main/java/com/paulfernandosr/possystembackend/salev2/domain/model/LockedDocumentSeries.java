package com.paulfernandosr.possystembackend.salev2.domain.model;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LockedDocumentSeries {
    private Long id;
    private Long stationId;
    private String docType;
    private String series;
    private Long nextNumber;
}
