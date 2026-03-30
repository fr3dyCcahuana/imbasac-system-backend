package com.paulfernandosr.possystembackend.catalog.infrastructure.adapter.output;

import com.paulfernandosr.possystembackend.catalog.domain.Province;
import com.paulfernandosr.possystembackend.catalog.domain.port.output.ProvinceCatalogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.util.Collection;

@Repository
@RequiredArgsConstructor
public class PostgresProvinceCatalogRepository implements ProvinceCatalogRepository {

    private final JdbcClient jdbcClient;

    @Override
    public Collection<Province> findByDepartmentCode(String departmentCode) {
        String sql = """
                SELECT id,
                       code,
                       department_code AS departmentCode,
                       name
                FROM province_catalog
                WHERE department_code = ?
                ORDER BY name
                """;

        return jdbcClient.sql(sql)
                .param(departmentCode)
                .query(Province.class)
                .list();
    }
}
