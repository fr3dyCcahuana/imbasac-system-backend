package com.paulfernandosr.possystembackend.salev2.infrastructure.adapter.output;

import com.paulfernandosr.possystembackend.salev2.domain.model.ProductSnapshot;
import com.paulfernandosr.possystembackend.salev2.domain.port.output.ProductSnapshotRepository;
import com.paulfernandosr.possystembackend.salev2.infrastructure.adapter.output.mapper.ProductSnapshotRowMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class PostgresProductSnapshotRepository implements ProductSnapshotRepository {

    private final JdbcClient jdbcClient;

    @Override
    public Optional<ProductSnapshot> findById(Long productId) {
        String sql = """
                SELECT
                    p.id AS product_id,
                    p.sku,
                    p.name,
                    p.presentation,
                    p.factor,
                    p.price_a,
                    p.price_b,
                    p.price_c,
                    p.price_d,
                    p.facturable_sunat,
                    p.affects_stock,
                    p.gift_allowed
                FROM product p
                WHERE p.id = ?
                """;

        return jdbcClient.sql(sql)
                .param(productId)
                .query(new ProductSnapshotRowMapper())
                .optional();
    }
}
