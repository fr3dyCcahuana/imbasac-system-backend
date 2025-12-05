package com.paulfernandosr.possystembackend.catalog.infrastructure.adapter.output;

import com.paulfernandosr.possystembackend.catalog.domain.ProductType;
import com.paulfernandosr.possystembackend.catalog.domain.port.output.ProductTypeCatalogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.util.Collection;

@Repository
@RequiredArgsConstructor
public class PostgresProductTypeCatalogRepository implements ProductTypeCatalogRepository {

    private final JdbcClient jdbcClient;

    @Override
    public void create(ProductType productType) {
        String sql = """
                INSERT INTO product_type_catalog (name)
                VALUES (?)
                """;

        jdbcClient.sql(sql)
                .param(productType.getName())
                .update();
    }

    @Override
    public Collection<ProductType> findAll() {
        String sql = """
                SELECT id, name
                FROM product_type_catalog
                ORDER BY name
                """;

        return jdbcClient.sql(sql)
                .query(ProductType.class)
                .list();
    }

    @Override
    public boolean existsByName(String name) {
        String sql = """
                SELECT EXISTS (
                    SELECT 1 FROM product_type_catalog WHERE name = ?
                )
                """;

        return jdbcClient.sql(sql)
                .param(name)
                .query(Boolean.class)
                .single();
    }
}
