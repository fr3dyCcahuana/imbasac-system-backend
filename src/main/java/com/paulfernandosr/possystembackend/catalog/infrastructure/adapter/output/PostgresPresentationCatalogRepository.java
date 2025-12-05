package com.paulfernandosr.possystembackend.catalog.infrastructure.adapter.output;

import com.paulfernandosr.possystembackend.catalog.domain.Presentation;
import com.paulfernandosr.possystembackend.catalog.domain.port.output.PresentationCatalogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.util.Collection;

@Repository
@RequiredArgsConstructor
public class PostgresPresentationCatalogRepository implements PresentationCatalogRepository {

    private final JdbcClient jdbcClient;

    @Override
    public void create(Presentation presentation) {
        String sql = """
                INSERT INTO presentation_catalog (name)
                VALUES (?)
                """;

        jdbcClient.sql(sql)
                .param(presentation.getName())
                .update();
    }

    @Override
    public Collection<Presentation> findAll() {
        String sql = """
                SELECT id, name
                FROM presentation_catalog
                ORDER BY name
                """;

        return jdbcClient.sql(sql)
                .query(Presentation.class)
                .list();
    }

    @Override
    public boolean existsByName(String name) {
        String sql = """
                SELECT EXISTS (
                    SELECT 1 FROM presentation_catalog WHERE name = ?
                )
                """;

        return jdbcClient.sql(sql)
                .param(name)
                .query(Boolean.class)
                .single();
    }
}
