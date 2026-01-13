package com.paulfernandosr.possystembackend.proformav2.domain.port.output;

import com.paulfernandosr.possystembackend.proformav2.infrastructure.adapter.output.model.LockedDocumentSeries;

public interface DocumentSeriesRepositoryV2 {
    LockedDocumentSeries lock(Long stationId, String docType, String series); // FOR UPDATE
    void incrementNextNumber(Long documentSeriesId);
}
