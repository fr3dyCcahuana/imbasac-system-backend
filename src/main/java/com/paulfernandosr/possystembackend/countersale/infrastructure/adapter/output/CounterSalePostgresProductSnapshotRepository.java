package com.paulfernandosr.possystembackend.countersale.infrastructure.adapter.output;

import com.paulfernandosr.possystembackend.countersale.domain.model.ProductSnapshot;
import com.paulfernandosr.possystembackend.countersale.domain.port.output.ProductSnapshotRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class CounterSalePostgresProductSnapshotRepository implements ProductSnapshotRepository {

    private final JdbcClient jdbcClient;

    @Override
    public ProductSnapshot findSnapshotById(Long productId) {
        String sql = """
            SELECT
                id,
                sku,
                name,
                presentation,
                factor,
                category,
                brand,
                model,
                affects_stock AS affectsStock,
                gift_allowed AS giftAllowed,
                COALESCE(manage_by_serial, FALSE) AS manageBySerial,
                price_a AS priceA,
                price_b AS priceB,
                price_c AS priceC,
                price_d AS priceD
            FROM product
            WHERE id = ?
        """;

        return jdbcClient.sql(sql)
                .param(productId)
                .query(ProductSnapshot.class)
                .optional()
                .orElse(null);
    }
}
