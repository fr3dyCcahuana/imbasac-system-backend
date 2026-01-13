package com.paulfernandosr.possystembackend.salev2.infrastructure.adapter.output;

import com.paulfernandosr.possystembackend.salev2.domain.model.ProductSnapshot;
import com.paulfernandosr.possystembackend.salev2.domain.port.output.ProductSnapshotRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository("saleV2PostgresProductSnapshotRepository")
@RequiredArgsConstructor
public class PostgresProductSnapshotRepository implements ProductSnapshotRepository {

    private final JdbcClient jdbcClient;

    @Override
    public ProductSnapshot findSnapshotById(Long productId) {
        // Nota: si tu columna manage_by_serial tiene otro nombre, ajusta aquí.
        String sql = """
            SELECT
                id,
                sku,
                name,
                presentation,
                factor,
                facturable_sunat AS facturableSunat,
                affects_stock    AS affectsStock,
                gift_allowed     AS giftAllowed,
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
