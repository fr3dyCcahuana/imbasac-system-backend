package com.paulfernandosr.possystembackend.salev2.domain.port.output;

import com.paulfernandosr.possystembackend.salev2.domain.model.DocType;

public interface DocumentSeriesRepository {

    LockedSeries lockAndGetNextNumber(Long stationId, DocType docType, String series);

    void incrementNextNumber(Long documentSeriesId);

    record LockedSeries(Long id, String series, Long number) {}
}
