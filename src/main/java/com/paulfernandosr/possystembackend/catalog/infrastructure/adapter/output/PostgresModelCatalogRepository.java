package com.paulfernandosr.possystembackend.catalog.infrastructure.adapter.output;

import com.paulfernandosr.possystembackend.catalog.domain.Model;
import com.paulfernandosr.possystembackend.catalog.domain.port.output.ModelCatalogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.util.Collection;

@Repository
@RequiredArgsConstructor
public class PostgresModelCatalogRepository implements ModelCatalogRepository {

    private final JdbcClient jdbcClient;

    @Override
    public void create(Model model) {
        String sql = """
                INSERT INTO model_catalog (name)
                VALUES (?)
                """;

        jdbcClient.sql(sql)
                .param(model.getName())
                .update();
    }

    @Override
    public Collection<Model> findAll() {
        String sql = """
                SELECT id, name
                FROM model_catalog
                ORDER BY name
                """;

        return jdbcClient.sql(sql)
                .query(Model.class)
                .list();
    }

    @Override
    public boolean existsByName(String name) {
        String sql = """
                SELECT EXISTS (
                    SELECT 1 FROM model_catalog WHERE name = ?
                )
                """;

        return jdbcClient.sql(sql)
                .param(name)
                .query(Boolean.class)
                .single();
    }
}
