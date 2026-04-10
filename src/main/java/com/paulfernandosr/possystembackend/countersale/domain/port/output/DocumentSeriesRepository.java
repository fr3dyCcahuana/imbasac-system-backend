package com.paulfernandosr.possystembackend.countersale.domain.port.output;

import com.paulfernandosr.possystembackend.countersale.domain.model.LockedDocumentSeries;

public interface DocumentSeriesRepository {
    LockedDocumentSeries lockSeries(String docType, String series);
    void incrementNextNumber(Long seriesId);
}
