package com.paulfernandosr.possystembackend.manualpdf.infrastructure.adapter.output.mapper;

import com.paulfernandosr.possystembackend.manualpdf.domain.ManualPdfModel;
import org.springframework.jdbc.core.RowMapper;

import java.sql.ResultSet;
import java.sql.SQLException;

public class ManualPdfModelRowMapper implements RowMapper<ManualPdfModel> {

    @Override
    public ManualPdfModel mapRow(ResultSet rs, int rowNum) throws SQLException {
        return new ManualPdfModel(
                rs.getLong("id"),
                rs.getLong("family_id"),
                rs.getString("code"),
                rs.getString("name")
        );
    }
}
