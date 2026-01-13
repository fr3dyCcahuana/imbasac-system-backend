package com.paulfernandosr.possystembackend.proformav2.infrastructure.adapter.output;

import com.paulfernandosr.possystembackend.proformav2.domain.exception.InvalidProformaV2Exception;
import com.paulfernandosr.possystembackend.proformav2.domain.port.output.DocumentSeriesRepositoryV2;
import com.paulfernandosr.possystembackend.proformav2.infrastructure.adapter.output.mapper.LockedDocumentSeriesRowMapper;
import com.paulfernandosr.possystembackend.proformav2.infrastructure.adapter.output.model.LockedDocumentSeries;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class PostgresDocumentSeriesRepositoryV2 implements DocumentSeriesRepositoryV2 {

    private final JdbcClient jdbcClient;

    @Override
    public LockedDocumentSeries lock(Long stationId, String docType, String series) {
        String sql = """
            SELECT id, station_id, doc_type, series, next_number, enabled
            FROM document_series
            WHERE station_id = ?
              AND doc_type = ?
              AND series = ?
            FOR UPDATE
            """;

        LockedDocumentSeries locked = jdbcClient.sql(sql)
                .params(stationId, docType, series)
                .query(new LockedDocumentSeriesRowMapper())
                .optional()
                .orElseThrow(() -> new InvalidProformaV2Exception(
                        "No existe serie para station=" + stationId + " docType=" + docType + " series=" + series
                ));

        if (!Boolean.TRUE.equals(locked.getEnabled())) {
            throw new InvalidProformaV2Exception("Serie deshabilitada: " + series + " (" + docType + ")");
        }

        return locked;
    }

    @Override
    public void incrementNextNumber(Long documentSeriesId) {
        String sql = """
            UPDATE document_series
            SET next_number = next_number + 1
            WHERE id = ?
            """;
        jdbcClient.sql(sql).param(documentSeriesId).update();
    }
}
