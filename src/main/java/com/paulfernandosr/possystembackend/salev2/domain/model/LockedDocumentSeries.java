package com.paulfernandosr.possystembackend.salev2.domain.model;

import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LockedDocumentSeries {
    private Long id;
    private Long stationId;
    private String docType;
    private String series;
    private Long nextNumber;
    private Boolean enabled;
}
