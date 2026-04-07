package com.paulfernandosr.possystembackend.manualpdf.infrastructure.adapter.output.mapper;

import com.paulfernandosr.possystembackend.manualpdf.domain.ManualPdfFamily;
import org.springframework.jdbc.core.RowMapper;

import java.sql.ResultSet;
import java.sql.SQLException;

public class ManualPdfFamilyRowMapper implements RowMapper<ManualPdfFamily> {

    @Override
    public ManualPdfFamily mapRow(ResultSet rs, int rowNum) throws SQLException {
        return new ManualPdfFamily(
                rs.getLong("id"),
                rs.getString("code"),
                rs.getString("name")
        );
    }
}
