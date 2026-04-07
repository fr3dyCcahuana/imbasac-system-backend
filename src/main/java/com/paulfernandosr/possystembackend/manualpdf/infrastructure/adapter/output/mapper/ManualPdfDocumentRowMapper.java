package com.paulfernandosr.possystembackend.manualpdf.infrastructure.adapter.output.mapper;

import com.paulfernandosr.possystembackend.manualpdf.domain.ManualPdfDocument;
import org.springframework.jdbc.core.RowMapper;

import java.sql.ResultSet;
import java.sql.SQLException;

public class ManualPdfDocumentRowMapper implements RowMapper<ManualPdfDocument> {

    @Override
    public ManualPdfDocument mapRow(ResultSet rs, int rowNum) throws SQLException {
        return new ManualPdfDocument(
                rs.getLong("id"),
                rs.getLong("model_id"),
                rs.getString("title"),
                rs.getInt("year_from"),
                rs.getInt("year_to"),
                rs.getString("file_name"),
                rs.getString("file_key"),
                rs.getString("mime_type"),
                rs.getObject("file_size", Long.class),
                rs.getBoolean("enabled")
        );
    }
}
