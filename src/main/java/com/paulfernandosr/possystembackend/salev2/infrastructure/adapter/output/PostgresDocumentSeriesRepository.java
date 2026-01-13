package com.paulfernandosr.possystembackend.salev2.infrastructure.adapter.output;

import com.paulfernandosr.possystembackend.salev2.domain.exception.InvalidSaleV2Exception;
import com.paulfernandosr.possystembackend.salev2.domain.model.DocType;
import com.paulfernandosr.possystembackend.salev2.domain.port.output.DocumentSeriesRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class PostgresDocumentSeriesRepository implements DocumentSeriesRepository {

    private final JdbcClient jdbcClient;

    @Override
    public LockedSeries lockAndGetNextNumber(Long stationId, DocType docType, String series) {
        String sql = """
                SELECT id, series, next_number
                FROM document_series
                WHERE station_id = ?
                  AND doc_type = ?
                  AND series = ?
                  AND enabled = TRUE
                FOR UPDATE
                """;

        return jdbcClient.sql(sql)
                .params(stationId, docType.name(), series)
                .query((rs, rowNum) -> new LockedSeries(
                        rs.getLong("id"),
                        rs.getString("series"),
                        rs.getLong("next_number")
                ))
                .optional()
                .orElseThrow(() -> new InvalidSaleV2Exception(
                        "No existe document_series habilitado para stationId=" + stationId +
                                ", docType=" + docType + ", series=" + series
                ));
    }

    @Override
    public void incrementNextNumber(Long documentSeriesId) {
        String sql = """
                UPDATE document_series
                   SET next_number = next_number + 1
                 WHERE id = ?
                """;

        jdbcClient.sql(sql)
                .param(documentSeriesId)
                .update();
    }
}
