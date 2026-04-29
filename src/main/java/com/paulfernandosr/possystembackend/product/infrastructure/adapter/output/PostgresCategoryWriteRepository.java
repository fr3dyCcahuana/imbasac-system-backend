package com.paulfernandosr.possystembackend.product.infrastructure.adapter.output;

import com.paulfernandosr.possystembackend.product.domain.port.output.CategoryWriteRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Set;

@Repository
@RequiredArgsConstructor
public class PostgresCategoryWriteRepository implements CategoryWriteRepository {

    private final JdbcClient jdbcClient;

    @Override
    public void insertMissing(Set<String> categoryNames) {
        if (categoryNames == null || categoryNames.isEmpty()) return;

        String sql = "INSERT INTO categories(name) VALUES (?) ON CONFLICT (name) DO NOTHING";

        for (String name : categoryNames) {
            if (name == null || name.isBlank()) continue;
            jdbcClient.sql(sql).params(name).update();
        }
    }

    @Override
    public List<String> findAllNames() {
        String sql = """
                SELECT name
                FROM categories
                WHERE NULLIF(TRIM(name), '') IS NOT NULL
                ORDER BY name ASC
                """;

        return jdbcClient.sql(sql)
                .query((rs, rowNum) -> rs.getString("name"))
                .list();
    }
}
