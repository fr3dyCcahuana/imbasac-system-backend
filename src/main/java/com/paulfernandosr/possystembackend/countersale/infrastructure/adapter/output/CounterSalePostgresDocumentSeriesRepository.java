package com.paulfernandosr.possystembackend.countersale.infrastructure.adapter.output;

import com.paulfernandosr.possystembackend.countersale.domain.exception.InvalidCounterSaleException;
import com.paulfernandosr.possystembackend.countersale.domain.model.LockedDocumentSeries;
import com.paulfernandosr.possystembackend.countersale.domain.port.output.DocumentSeriesRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class CounterSalePostgresDocumentSeriesRepository implements DocumentSeriesRepository {

    private final JdbcClient jdbcClient;

    @Override
    public LockedDocumentSeries lockSeries(String docType, String series) {
        String sql = """
            SELECT id,
                   station_id AS stationId,
                   doc_type AS docType,
                   series,
                   next_number AS nextNumber,
                   enabled
              FROM document_series
             WHERE doc_type = ?
               AND series = ?
               AND enabled = TRUE
             FOR UPDATE
        """;

        return jdbcClient.sql(sql)
                .params(docType, series)
                .query(LockedDocumentSeries.class)
                .optional()
                .orElseThrow(() -> new InvalidCounterSaleException(
                        "No existe serie habilitada para docType=" + docType + " series=" + series
                ));
    }

    @Override
    public void incrementNextNumber(Long seriesId) {
        jdbcClient.sql("UPDATE document_series SET next_number = next_number + 1 WHERE id = ?")
                .param(seriesId)
                .update();
    }
}
