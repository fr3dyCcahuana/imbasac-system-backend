package com.paulfernandosr.possystembackend.salev2.infrastructure.adapter.output;

import com.paulfernandosr.possystembackend.salev2.domain.port.output.ProductStockMovementRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;

@Repository
@RequiredArgsConstructor
public class PostgresProductStockMovementRepository implements ProductStockMovementRepository {

    private final JdbcClient jdbcClient;

    @Override
    public void createOutSaleMovement(Long productId, BigDecimal quantityOut, Long saleItemId) {
        String sql = """
            INSERT INTO product_stock_movement(
                product_id,
                movement_type,
                source_table,
                source_id,
                quantity_out,
                created_at
            )
            VALUES (?,?,?,?,?, NOW())
            """;

        jdbcClient.sql(sql)
                .params(
                        productId,
                        "OUT_SALE",
                        "sale_item",
                        saleItemId,
                        quantityOut
                )
                .update();
    }
}
