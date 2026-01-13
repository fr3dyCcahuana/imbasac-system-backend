package com.paulfernandosr.possystembackend.proformav2.infrastructure.adapter.output;

import com.paulfernandosr.possystembackend.proformav2.domain.exception.InvalidProformaV2Exception;
import com.paulfernandosr.possystembackend.proformav2.domain.port.output.ProductSnapshotRepository;
import com.paulfernandosr.possystembackend.proformav2.infrastructure.adapter.output.mapper.ProductSnapshotRowMapper;
import com.paulfernandosr.possystembackend.proformav2.infrastructure.adapter.output.model.ProductSnapshot;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository("proformaV2PostgresProductSnapshotRepository")
@RequiredArgsConstructor
public class PostgresProductSnapshotRepository implements ProductSnapshotRepository {

    private final JdbcClient jdbcClient;

    @Override
    public ProductSnapshot getById(Long productId) {
        String sql = """
            SELECT
              p.id AS product_id,
              p.sku,
              p.name,
              p.presentation,
              p.factor,
              p.manage_by_serial,
              p.facturable_sunat,
              p.affects_stock,
              p.price_a,
              p.price_b,
              p.price_c,
              p.price_d
            FROM product p
            WHERE p.id = ?
            """;

        return jdbcClient.sql(sql)
                .param(productId)
                .query(new ProductSnapshotRowMapper())
                .optional()
                .orElseThrow(() -> new InvalidProformaV2Exception("Producto no encontrado: " + productId));
    }
}
