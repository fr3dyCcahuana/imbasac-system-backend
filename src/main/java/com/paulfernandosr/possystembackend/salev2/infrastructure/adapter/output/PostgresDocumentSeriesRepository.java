package com.paulfernandosr.possystembackend.salev2.infrastructure.adapter.output;

import com.paulfernandosr.possystembackend.salev2.domain.exception.InvalidSaleV2Exception;
import com.paulfernandosr.possystembackend.salev2.domain.model.LockedDocumentSeries;
import com.paulfernandosr.possystembackend.salev2.domain.port.output.DocumentSeriesRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class PostgresDocumentSeriesRepository implements DocumentSeriesRepository {

    private final JdbcClient jdbcClient;

    @Override
    public LockedDocumentSeries lockSeries(String docType, String series) {
        String sql = """
            SELECT id,
                   station_id AS stationId,
                   doc_type   AS docType,
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
                .orElseThrow(() -> new InvalidSaleV2Exception(
                        "No existe serie para docType=" + docType + " series=" + series
                ));
    }

    @Override
    public void incrementNextNumber(Long seriesId) {
        jdbcClient.sql("UPDATE document_series SET next_number = next_number + 1 WHERE id = ?")
                .param(seriesId)
                .update();
    }
}
