package com.paulfernandosr.possystembackend.catalog.infrastructure.adapter.output;

import com.paulfernandosr.possystembackend.catalog.domain.Department;
import com.paulfernandosr.possystembackend.catalog.domain.port.output.DepartmentCatalogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.util.Collection;

@Repository
@RequiredArgsConstructor
public class PostgresDepartmentCatalogRepository implements DepartmentCatalogRepository {

    private final JdbcClient jdbcClient;

    @Override
    public Collection<Department> findAll() {
        String sql = """
                SELECT id, code, name
                FROM department_catalog
                ORDER BY name
                """;

        return jdbcClient.sql(sql)
                .query(Department.class)
                .list();
    }
}
