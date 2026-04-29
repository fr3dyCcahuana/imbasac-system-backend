package com.paulfernandosr.possystembackend.product.infrastructure.adapter.output;

import com.paulfernandosr.possystembackend.product.domain.port.output.BrandWriteRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Set;

@Repository
@RequiredArgsConstructor
public class PostgresBrandWriteRepository implements BrandWriteRepository {

    private final JdbcClient jdbcClient;

    @Override
    public void insertMissing(Set<String> brands) {
        if (brands == null || brands.isEmpty()) return;

        String sql = "INSERT INTO brand_catalog(name) VALUES (?) ON CONFLICT (name) DO NOTHING";
        for (String b : brands) {
            if (b == null || b.isBlank()) continue;
            jdbcClient.sql(sql).param(b).update();
        }
    }

    @Override
    public List<String> findAllNames() {
        String sql = """
                SELECT name
                FROM brand_catalog
                WHERE NULLIF(TRIM(name), '') IS NOT NULL
                ORDER BY name ASC
                """;

        return jdbcClient.sql(sql)
                .query((rs, rowNum) -> rs.getString("name"))
                .list();
    }
}
