package com.paulfernandosr.possystembackend.catalog.infrastructure.adapter.output;

import com.paulfernandosr.possystembackend.catalog.domain.OriginCountry;
import com.paulfernandosr.possystembackend.catalog.domain.port.output.OriginCountryCatalogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.util.Collection;

@Repository
@RequiredArgsConstructor
public class PostgresOriginCountryCatalogRepository implements OriginCountryCatalogRepository {

    private final JdbcClient jdbcClient;

    @Override
    public void create(OriginCountry originCountry) {
        String sql = """
                INSERT INTO origin_country_catalog (name)
                VALUES (?)
                """;

        jdbcClient.sql(sql)
                .param(originCountry.getName())
                .update();
    }

    @Override
    public Collection<OriginCountry> findAll() {
        String sql = """
                SELECT id, name
                FROM origin_country_catalog
                ORDER BY name
                """;

        return jdbcClient.sql(sql)
                .query(OriginCountry.class)
                .list();
    }

    @Override
    public boolean existsByName(String name) {
        String sql = """
                SELECT EXISTS (
                    SELECT 1 FROM origin_country_catalog WHERE name = ?
                )
                """;

        return jdbcClient.sql(sql)
                .param(name)
                .query(Boolean.class)
                .single();
    }
}
