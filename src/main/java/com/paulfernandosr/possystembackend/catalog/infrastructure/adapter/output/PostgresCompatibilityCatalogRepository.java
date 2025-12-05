package com.paulfernandosr.possystembackend.catalog.infrastructure.adapter.output;

import com.paulfernandosr.possystembackend.catalog.domain.Compatibility;
import com.paulfernandosr.possystembackend.catalog.domain.port.output.CompatibilityCatalogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.util.Collection;

@Repository
@RequiredArgsConstructor
public class PostgresCompatibilityCatalogRepository implements CompatibilityCatalogRepository {

    private final JdbcClient jdbcClient;

    @Override
    public void create(Compatibility compatibility) {
        String sql = """
                INSERT INTO compatibility_catalog (name)
                VALUES (?)
                """;

        jdbcClient.sql(sql)
                .param(compatibility.getName())
                .update();
    }

    @Override
    public Collection<Compatibility> findAll() {
        String sql = """
                SELECT id, name
                FROM compatibility_catalog
                ORDER BY name
                """;

        return jdbcClient.sql(sql)
                .query(Compatibility.class)
                .list();
    }

    @Override
    public boolean existsByName(String name) {
        String sql = """
                SELECT EXISTS (
                    SELECT 1 FROM compatibility_catalog WHERE name = ?
                )
                """;

        return jdbcClient.sql(sql)
                .param(name)
                .query(Boolean.class)
                .single();
    }
}
