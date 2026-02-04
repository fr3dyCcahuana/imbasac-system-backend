package com.paulfernandosr.possystembackend.catalog.infrastructure.adapter.output;

import com.paulfernandosr.possystembackend.catalog.domain.Brand;
import com.paulfernandosr.possystembackend.catalog.domain.port.output.BrandCatalogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.util.Collection;

@Repository
@RequiredArgsConstructor
public class PostgresBrandCatalogRepository implements BrandCatalogRepository {

    private final JdbcClient jdbcClient;

    @Override
    public void create(Brand brand) {
        String sql = """
                INSERT INTO brand_catalog (name)
                VALUES (?)
                """;

        jdbcClient.sql(sql)
                .param(brand.getName())
                .update();
    }

    @Override
    public Collection<Brand> findAll() {
        String sql = """
                SELECT id, name
                FROM brand_catalog
                ORDER BY name
                """;

        return jdbcClient.sql(sql)
                .query(Brand.class)
                .list();
    }

    @Override
    public boolean existsByName(String name) {
        String sql = """
                SELECT EXISTS (
                    SELECT 1 FROM brand_catalog WHERE name = ?
                )
                """;

        return jdbcClient.sql(sql)
                .param(name)
                .query(Boolean.class)
                .single();
    }
}
