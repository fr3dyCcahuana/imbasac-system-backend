package com.paulfernandosr.possystembackend.catalog.infrastructure.adapter.output;

import com.paulfernandosr.possystembackend.catalog.domain.District;
import com.paulfernandosr.possystembackend.catalog.domain.port.output.DistrictCatalogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.util.Collection;

@Repository
@RequiredArgsConstructor
public class PostgresDistrictCatalogRepository implements DistrictCatalogRepository {

    private final JdbcClient jdbcClient;

    @Override
    public Collection<District> findByProvinceCode(String provinceCode) {
        String sql = """
                SELECT id,
                       code,
                       department_code AS departmentCode,
                       province_code AS provinceCode,
                       name
                FROM district_catalog
                WHERE province_code = ?
                ORDER BY name
                """;

        return jdbcClient.sql(sql)
                .param(provinceCode)
                .query(District.class)
                .list();
    }
}
