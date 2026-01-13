package com.paulfernandosr.possystembackend.salev2.domain.port.output;

import com.paulfernandosr.possystembackend.salev2.domain.model.LockedDocumentSeries;

public interface DocumentSeriesRepository {
    LockedDocumentSeries lockSeries(Long stationId, String docType, String series);
    void incrementNextNumber(Long seriesId);
}
