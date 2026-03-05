package com.paulfernandosr.possystembackend.product.infrastructure.adapter.output;

import com.paulfernandosr.possystembackend.product.domain.port.output.ModelWriteRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.util.Set;

@Repository
@RequiredArgsConstructor
public class PostgresModelWriteRepository implements ModelWriteRepository {

    private final JdbcClient jdbcClient;

    @Override
    public void insertMissing(Set<String> models) {
        if (models == null || models.isEmpty()) return;

        String sql = "INSERT INTO model_catalog(name) VALUES (?) ON CONFLICT (name) DO NOTHING";
        for (String m : models) {
            if (m == null || m.isBlank()) continue;
            jdbcClient.sql(sql).param(m).update();
        }
    }
}
