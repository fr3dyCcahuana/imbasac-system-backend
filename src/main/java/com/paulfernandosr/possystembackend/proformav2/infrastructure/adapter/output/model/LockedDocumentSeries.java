package com.paulfernandosr.possystembackend.proformav2.infrastructure.adapter.output.model;

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
    private Boolean enabled;
}
