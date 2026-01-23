package com.paulfernandosr.possystembackend.product.infrastructure.adapter.output;

import com.paulfernandosr.possystembackend.product.domain.port.output.CategoryWriteRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

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
            jdbcClient.sql(sql).params(name).update();
        }
    }
}
