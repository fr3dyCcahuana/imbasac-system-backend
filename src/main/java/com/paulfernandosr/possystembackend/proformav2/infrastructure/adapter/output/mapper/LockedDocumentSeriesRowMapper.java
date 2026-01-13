package com.paulfernandosr.possystembackend.proformav2.infrastructure.adapter.output.mapper;

import com.paulfernandosr.possystembackend.proformav2.infrastructure.adapter.output.model.LockedDocumentSeries;
import org.springframework.jdbc.core.RowMapper;

import java.sql.ResultSet;
import java.sql.SQLException;

public class LockedDocumentSeriesRowMapper implements RowMapper<LockedDocumentSeries> {
    @Override
    public LockedDocumentSeries mapRow(ResultSet rs, int rowNum) throws SQLException {
        return LockedDocumentSeries.builder()
                .id(rs.getLong("id"))
                .stationId(rs.getLong("station_id"))
                .docType(rs.getString("doc_type"))
                .series(rs.getString("series"))
                .nextNumber(rs.getLong("next_number"))
                .enabled(rs.getBoolean("enabled"))
                .build();
    }
}
